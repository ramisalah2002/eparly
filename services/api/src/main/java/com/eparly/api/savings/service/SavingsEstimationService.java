package com.eparly.api.savings.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.service.ComparisonService;
import com.eparly.api.comparison.strategy.ComparisonStrategy;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.savings.domain.SavingsStatus;
import com.eparly.api.savings.dto.SavingsEstimateRequest;
import com.eparly.api.savings.dto.SavingsEstimateResponse;
import com.eparly.api.savings.dto.SubscriptionCostSnapshot;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SavingsEstimationService {

    private final ComparisonService comparisonService;
    private final SubscriptionBaselineService baselineService;
    private final PricingCalculationService pricingService;
    private final PromotionCalculationService promotionService;
    private final SwitchingCostService switchingCostService;
    private final SavingsEligibilityValidator eligibilityValidator;
    private final SavingsExplanationBuilder explanationBuilder;
    private final CommercialOfferRepository offerRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferEvidenceRepository evidenceRepository;
    private final List<ComparisonStrategy> strategies;

    public SavingsEstimationService(
            ComparisonService comparisonService,
            SubscriptionBaselineService baselineService,
            PricingCalculationService pricingService,
            PromotionCalculationService promotionService,
            SwitchingCostService switchingCostService,
            SavingsEligibilityValidator eligibilityValidator,
            SavingsExplanationBuilder explanationBuilder,
            CommercialOfferRepository offerRepository,
            OfferPricingRepository pricingRepository,
            OfferEvidenceRepository evidenceRepository,
            List<ComparisonStrategy> strategies) {
        this.comparisonService = comparisonService;
        this.baselineService = baselineService;
        this.pricingService = pricingService;
        this.promotionService = promotionService;
        this.switchingCostService = switchingCostService;
        this.eligibilityValidator = eligibilityValidator;
        this.explanationBuilder = explanationBuilder;
        this.offerRepository = offerRepository;
        this.pricingRepository = pricingRepository;
        this.evidenceRepository = evidenceRepository;
        this.strategies = strategies;
    }

    /**
     * Estime les économies nettes potentielles pour le remplacement d'un abonnement par une offre alternative.
     */
    public SavingsEstimateResponse estimate(@AuthenticationPrincipal Jwt jwt, SavingsEstimateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requête d'estimation invalide");
        }

        UUID userId = JwtUserId.from(jwt);

        // 1. Récupérer le coût actuel de l'abonnement
        SubscriptionCostSnapshot baseline = baselineService.getCurrentCost(userId, request.subscriptionId());

        // Abonnement résilié ou en pause : pas d'économie à estimer sur un coût actif
        if (baseline.declaredStatus() == SubscriptionStatus.CANCELED) {
            return buildCanceledResponse(baseline, request);
        }
        if (baseline.declaredStatus() == SubscriptionStatus.PAUSED) {
            return buildPausedResponse(baseline, request);
        }

        // 2. Valider l'offre alternative
        LocalDate now = LocalDate.now();
        Integer horizonMonths = request.horizonMonths() != null ? request.horizonMonths() : 12;
        LocalDate simulationStartDate = now;

        SavingsEligibilityValidator.ValidatedOffer validated =
                eligibilityValidator.validateOffer(request.offerId(), "FR", simulationStartDate);

        CommercialOffer offer = validated.offer();
        OfferPricing pricing = validated.pricing();
        OfferEvidence evidence = validated.evidence();

        // 3. Vérifier la compatibilité via le moteur de comparaison (étape 10B)
        // Le backend ne fait pas confiance à un statut transmis par le client.
        ComparisonStrategy strategy = findStrategy(request.sector());
        ComparisonRequest comparisonRequest = new ComparisonRequest(
                request.subscriptionId(),
                request.sector(),
                request.requirements() != null ? request.requirements() : Map.of()
        );
        List<OfferEvidence> evidenceList = evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(offer.getId());
        ComparisonOfferResult comparisonResult = strategy.evaluateOffer(
                comparisonRequest, offer, pricing, evidenceList
        );
        if (comparisonResult.status() == ComparisonStatus.INCOMPATIBLE) {
            return buildIncompatibleResponse(baseline, request, offer);
        }
        if (comparisonResult.status() == ComparisonStatus.NOT_ELIGIBLE) {
            return buildNotEligibleResponse(baseline, request, offer);
        }
        if (comparisonResult.status() == ComparisonStatus.NEEDS_INFORMATION) {
            return buildNeedsInformationResponse(baseline, request, offer);
        }

        // 4. Calcul des coûts
        Long currentMonthlyCost = baseline.amountMinor();
        RecurringFrequency currentFreq = baseline.frequency();

        // Convertir le coût actuel en équivalent mensuel
        OfferBillingPeriod currentPeriod = convertFrequencyToBillingPeriod(currentFreq);
        Long currentTotalCost = pricingService.calculateTotalCost(currentMonthlyCost, currentPeriod, horizonMonths);

        // Coût alternatif avec promotion
        // Le prix pendant la promotion = amountMinor * (1 - promotionPercent/100), arrondi au centime près
        OfferBillingPeriod altPeriod = pricing.getBillingPeriod();
        Long promotionPriceMinor = null;
        if (pricing.getAmountMinor() != null && pricing.getPromotionPercent() != null) {
            promotionPriceMinor = BigDecimal.valueOf(pricing.getAmountMinor())
                    .multiply(BigDecimal.valueOf(100 - pricing.getPromotionPercent()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
        }
        Long altTotalCost = pricingService.calculateTotalCostWithPromotion(
                pricing.getAmountMinor(),
                altPeriod,
                promotionPriceMinor,
                pricing.getPromotionDurationMonths(),
                horizonMonths
        );

        // 5. Frais de changement
        Long cancellationFee = request.userKnownCancellationFeeMinor();
        Long activationFee = pricing.getActivationFeeMinor();
        // Autres frais fournis par l'utilisateur (inconnus si non renseignés)
        Long otherFees = request.userKnownOtherFeesMinor();

        SwitchingCostService.SwitchingCostResult switchingResult =
                switchingCostService.calculateWithMissingInfo(cancellationFee, activationFee, otherFees);
        Long totalSwitchingCost = switchingResult.totalSwitchingCostMinor();
        List<String> missingFees = switchingResult.missingFees();

        // 6. Calcul de l'économie nette
        Long netSavings = null;
        boolean currentCostMissing = currentTotalCost == null;
        boolean alternativeCostMissing = altTotalCost == null;
        boolean switchingCostMissing = !missingFees.isEmpty();

        if (!currentCostMissing && !alternativeCostMissing && !switchingCostMissing) {
            try {
                netSavings = Math.subtractExact(
                        Math.subtractExact(currentTotalCost, altTotalCost),
                        totalSwitchingCost
                );
            } catch (ArithmeticException e) {
                netSavings = null;
            }
        }

        // 7. Déterminer le statut
        SavingsStatus status;
        if (currentCostMissing || alternativeCostMissing || switchingCostMissing) {
            status = SavingsStatus.INSUFFICIENT_DATA;
        } else if (netSavings != null && netSavings > 0) {
            status = SavingsStatus.SAVINGS_POSSIBLE;
        } else {
            status = SavingsStatus.NO_SAVINGS;
        }

        // 8. Construire les explications
        List<String> assumptions = explanationBuilder.buildAssumptions(
                horizonMonths,
                promotionService.isPromotionApplicable(pricing, simulationStartDate),
                totalSwitchingCost,
                missingFees,
                baseline.costSource() == CostSource.TRANSACTION_OBSERVED
        );

        List<String> missingInformation = explanationBuilder.buildMissingInformation(
                currentCostMissing,
                missingFees,
                !promotionService.isPromotionApplicable(pricing, simulationStartDate)
                        && pricing.getPromotionPercent() != null
        );

        List<String> calculationSteps = explanationBuilder.buildCalculationSteps(
                currentTotalCost,
                altTotalCost,
                totalSwitchingCost,
                netSavings
        );

        String globalExplanation = explanationBuilder.buildGlobalExplanation(
                status, netSavings, baseline.currency()
        );

        // 9. Construire la réponse (33 arguments dans l'ordre du record SavingsEstimateResponse)
        Long altMonthlyCost = pricingService.calculateEffectiveMonthlyCostWithPromotion(
                pricing.getAmountMinor(),
                altPeriod,
                pricing.getPriceAfterPromotionMinor(),
                pricing.getPromotionDurationMonths(),
                horizonMonths
        );

        Long currentAnnualCost = pricingService.calculateTotalCost(currentMonthlyCost, currentPeriod, 12);
        Long altAnnualCost = pricingService.calculateTotalCostWithPromotion(
                pricing.getAmountMinor(),
                altPeriod,
                pricing.getPriceAfterPromotionMinor(),
                pricing.getPromotionDurationMonths(),
                12
        );

        Long estimatedMonthlySavings = null;
        if (netSavings != null && horizonMonths > 0) {
            try {
                estimatedMonthlySavings = netSavings / horizonMonths;
            } catch (ArithmeticException ignored) {
            }
        }

        Instant verifiedAt = evidence != null ? evidence.getVerifiedAt() : null;
        String officialUrl = evidence != null ? evidence.getOfficialUrl() : null;

        return new SavingsEstimateResponse(
                // 1-3: Abonnement actuel
                baseline.subscriptionId(),
                baseline.subscriptionName(),
                baseline.currentProvider(),
                // 4-7: Offre alternative
                offer.getId(),
                offer.getCommercialName(),
                offer.getProvider().getName(),
                offer.getSector(),
                // 8: Statut
                status,
                // 9-10: Horizon et date
                horizonMonths,
                simulationStartDate,
                // 11-14: Coût actuel
                currentTotalCost,
                baseline.costSource(),
                currentMonthlyCost,
                currentAnnualCost,
                // 15-17: Coût alternatif
                altTotalCost,
                altMonthlyCost,
                altAnnualCost,
                // 18-20: Promotion
                pricing.getPriceAfterPromotionMinor(),
                pricing.getPromotionDurationMonths(),
                pricing.getPriceAfterPromotionMinor(),
                // 21-24: Frais de changement
                cancellationFee,
                activationFee,
                otherFees,
                totalSwitchingCost,
                // 25-26: Résultat
                netSavings,
                estimatedMonthlySavings,
                // 27: Explication
                globalExplanation,
                // 28-30: Listes
                assumptions,
                missingInformation,
                calculationSteps,
                // 31-32: Traçabilité
                officialUrl,
                verifiedAt,
                // 33: Date d'estimation
                Instant.now()
        );
    }

    private SavingsEstimateResponse buildCanceledResponse(SubscriptionCostSnapshot baseline, SavingsEstimateRequest request) {
        Integer horizonMonths = request.horizonMonths() != null ? request.horizonMonths() : 12;
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Abonnement résilié : aucun coût actif à comparer.");
        assumptions.add("Aucune économie nette ne peut être estimée sur un abonnement déjà résilié.");

        List<String> missingInformation = new ArrayList<>();
        missingInformation.add("Abonnement résilié : le coût actuel n'est plus actif.");

        List<String> calculationSteps = new ArrayList<>();
        calculationSteps.add("1. Abonnement résilié : coût actuel = 0 (plus aucun paiement récurrent).");
        calculationSteps.add("2. Aucune comparaison d'économies n'est pertinente.");

        return new SavingsEstimateResponse(
                // 1-3: Abonnement actuel
                baseline.subscriptionId(),
                baseline.subscriptionName(),
                baseline.currentProvider(),
                // 4-7: Offre alternative (nulle - pas de comparaison)
                null, null, null, null,
                // 8: Statut
                SavingsStatus.INSUFFICIENT_DATA,
                // 9-10: Horizon et date
                horizonMonths,
                LocalDate.now(),
                // 11-14: Coût actuel (nul)
                null, null, null, null,
                // 15-17: Coût alternatif (nul)
                null, null, null,
                // 18-20: Promotion (nulle)
                null, null, null,
                // 21-24: Frais de changement (nuls)
                null, null, null, null,
                // 25-26: Résultat (nuls)
                null, null,
                // 27: Explication
                "Abonnement résilié : aucune économie nette ne peut être estimée.",
                // 28-30: Listes
                assumptions,
                missingInformation,
                calculationSteps,
                // 31-32: Traçabilité (nuls)
                null, null,
                // 33: Date d'estimation
                Instant.now()
        );
    }

    private SavingsEstimateResponse buildPausedResponse(SubscriptionCostSnapshot baseline, SavingsEstimateRequest request) {
        Integer horizonMonths = request.horizonMonths() != null ? request.horizonMonths() : 12;
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Abonnement en pause : le coût actuel n'est pas actif durant la pause.");
        assumptions.add("L'estimation d'économies suppose une reprise de l'abonnement au tarif observé/déclaré.");

        List<String> missingInformation = new ArrayList<>();
        missingInformation.add("Abonnement en pause : le coût actuel peut ne pas s'appliquer pendant la période de pause.");

        List<String> calculationSteps = new ArrayList<>();
        calculationSteps.add("1. Abonnement en pause : coût actuel suspendu.");
        calculationSteps.add("2. L'estimation est conditionnelle à la reprise de l'abonnement.");

        return new SavingsEstimateResponse(
                // 1-3: Abonnement actuel
                baseline.subscriptionId(),
                baseline.subscriptionName(),
                baseline.currentProvider(),
                // 4-7: Offre alternative (nulle - pas de comparaison)
                null, null, null, null,
                // 8: Statut
                SavingsStatus.INSUFFICIENT_DATA,
                // 9-10: Horizon et date
                horizonMonths,
                LocalDate.now(),
                // 11-14: Coût actuel (nul)
                null, null, null, null,
                // 15-17: Coût alternatif (nul)
                null, null, null,
                // 18-20: Promotion (nulle)
                null, null, null,
                // 21-24: Frais de changement (nuls)
                null, null, null, null,
                // 25-26: Résultat (nuls)
                null, null,
                // 27: Explication
                "Abonnement en pause : aucune économie nette ne peut être estimée de manière fiable.",
                // 28-30: Listes
                assumptions,
                missingInformation,
                calculationSteps,
                // 31-32: Traçabilité (nuls)
                null, null,
                // 33: Date d'estimation
                Instant.now()
        );
    }

    private OfferBillingPeriod convertFrequencyToBillingPeriod(RecurringFrequency freq) {
        if (freq == null) return OfferBillingPeriod.UNKNOWN;
        return switch (freq) {
            case WEEKLY -> OfferBillingPeriod.WEEKLY;
            case MONTHLY -> OfferBillingPeriod.MONTHLY;
            case QUARTERLY -> OfferBillingPeriod.QUARTERLY;
            case YEARLY -> OfferBillingPeriod.YEARLY;
            case UNKNOWN -> OfferBillingPeriod.UNKNOWN;
        };
    }

    private ComparisonStrategy findStrategy(OfferSector sector) {
        for (ComparisonStrategy s : strategies) {
            if (s.supportedSector() == sector) return s;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Aucune stratégie de comparaison pour le secteur: " + sector);
    }

    private SavingsEstimateResponse buildIncompatibleResponse(SubscriptionCostSnapshot baseline,
                                                              SavingsEstimateRequest request,
                                                              CommercialOffer offer) {
        Integer horizonMonths = request.horizonMonths() != null ? request.horizonMonths() : 12;
        List<String> assumptions = new ArrayList<>();
        assumptions.add("L'offre alternative n'est pas compatible avec les critères de l'utilisateur.");
        assumptions.add("Aucune économie nette ne peut être calculée pour une offre incompatible.");

        List<String> missingInformation = new ArrayList<>();
        missingInformation.add("Critères de comparaison non satisfaits par l'offre alternative.");

        List<String> calculationSteps = new ArrayList<>();
        calculationSteps.add("1. Vérification de compatibilité : offre non compatible.");
        calculationSteps.add("2. Aucun calcul d'économies n'est effectué pour une offre incompatible.");

        return new SavingsEstimateResponse(
                baseline.subscriptionId(),
                baseline.subscriptionName(),
                baseline.currentProvider(),
                offer.getId(),
                offer.getCommercialName(),
                offer.getProvider().getName(),
                offer.getSector(),
                SavingsStatus.INCOMPATIBLE_OFFER,
                horizonMonths,
                LocalDate.now(),
                null, null, null, null,
                null, null, null,
                null, null, null,
                null, null, null, null,
                null, null,
                "L'offre alternative n'est pas compatible avec vos critères. Aucun calcul d'économies n'est possible.",
                assumptions,
                missingInformation,
                calculationSteps,
                null, null,
                Instant.now()
        );
    }

    private SavingsEstimateResponse buildNotEligibleResponse(SubscriptionCostSnapshot baseline,
                                                             SavingsEstimateRequest request,
                                                             CommercialOffer offer) {
        Integer horizonMonths = request.horizonMonths() != null ? request.horizonMonths() : 12;
        List<String> assumptions = new ArrayList<>();
        assumptions.add("L'utilisateur ne remplit pas les conditions d'éligibilité de cette offre.");
        assumptions.add("Aucune économie nette ne peut être calculée pour une offre non éligible.");

        List<String> missingInformation = new ArrayList<>();
        missingInformation.add("Conditions d'éligibilité commerciales non remplies.");

        List<String> calculationSteps = new ArrayList<>();
        calculationSteps.add("1. Vérification d'éligibilité : utilisateur non éligible.");
        calculationSteps.add("2. Aucun calcul d'économies n'est effectué pour une offre non éligible.");

        return new SavingsEstimateResponse(
                baseline.subscriptionId(),
                baseline.subscriptionName(),
                baseline.currentProvider(),
                offer.getId(),
                offer.getCommercialName(),
                offer.getProvider().getName(),
                offer.getSector(),
                SavingsStatus.NOT_ELIGIBLE,
                horizonMonths,
                LocalDate.now(),
                null, null, null, null,
                null, null, null,
                null, null, null,
                null, null, null, null,
                null, null,
                "Vous ne remplissez pas les conditions d'éligibilité de cette offre. Aucun calcul d'économies n'est possible.",
                assumptions,
                missingInformation,
                calculationSteps,
                null, null,
                Instant.now()
        );
    }

    private SavingsEstimateResponse buildNeedsInformationResponse(SubscriptionCostSnapshot baseline,
                                                                  SavingsEstimateRequest request,
                                                                  CommercialOffer offer) {
        Integer horizonMonths = request.horizonMonths() != null ? request.horizonMonths() : 12;
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Des informations essentielles manquent pour établir l'équivalence de l'offre.");
        assumptions.add("Aucune économie nette ne peut être calculée tant que ces informations sont manquantes.");

        List<String> missingInformation = new ArrayList<>();
        missingInformation.add("Informations essentielles manquantes pour l'évaluation de l'offre alternative.");

        List<String> calculationSteps = new ArrayList<>();
        calculationSteps.add("1. Vérification de compatibilité : informations manquantes.");
        calculationSteps.add("2. Aucun calcul d'économies n'est effectué tant que les informations sont manquantes.");

        return new SavingsEstimateResponse(
                baseline.subscriptionId(),
                baseline.subscriptionName(),
                baseline.currentProvider(),
                offer.getId(),
                offer.getCommercialName(),
                offer.getProvider().getName(),
                offer.getSector(),
                SavingsStatus.INSUFFICIENT_DATA,
                horizonMonths,
                LocalDate.now(),
                null, null, null, null,
                null, null, null,
                null, null, null,
                null, null, null, null,
                null, null,
                "Des informations essentielles manquent pour évaluer cette offre. Aucun calcul d'économies n'est possible.",
                assumptions,
                missingInformation,
                calculationSteps,
                null, null,
                Instant.now()
        );
    }
}
