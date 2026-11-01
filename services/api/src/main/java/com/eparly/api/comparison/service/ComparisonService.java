package com.eparly.api.comparison.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonCriterionDetail;
import com.eparly.api.comparison.dto.ComparisonMetadataResponse;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.dto.ComparisonResponse;
import com.eparly.api.comparison.strategy.ComparisonStrategy;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.service.OfferAvailabilityService;
import com.eparly.api.savings.service.PromotionCalculationService;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ComparisonService {

    private final SubscriptionRepository subscriptionRepository;
    private final CommercialOfferRepository offerRepository;
    private final OfferEligibilityService eligibilityService;
    private final ComparisonCriteriaValidator validator;
    private final OfferAvailabilityService availabilityService;
    private final PromotionCalculationService promotionService;
    private final List<ComparisonStrategy> strategies;

    public ComparisonService(SubscriptionRepository subscriptionRepository,
                             CommercialOfferRepository offerRepository,
                             OfferEligibilityService eligibilityService,
                             ComparisonCriteriaValidator validator,
                             OfferAvailabilityService availabilityService,
                             PromotionCalculationService promotionService,
                             List<ComparisonStrategy> strategies) {
        this.subscriptionRepository = subscriptionRepository;
        this.offerRepository = offerRepository;
        this.eligibilityService = eligibilityService;
        this.validator = validator;
        this.availabilityService = availabilityService;
        this.promotionService = promotionService;
        this.strategies = strategies;
    }

    /**
     * Évalue les offres alternatives pour un abonnement donné.
     */
    public ComparisonResponse evaluate(@AuthenticationPrincipal Jwt jwt, ComparisonRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requête de comparaison invalide");
        }

        UUID userId = JwtUserId.from(jwt);

        // Récupérer l'abonnement appartenant à l'utilisateur
        Subscription subscription = subscriptionRepository.findByIdAndUserId(request.subscriptionId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Abonnement introuvable"));

        ComparisonStrategy strategy = validateRequest(request);

        // Récupérer les offres publiquement consultables pour ce secteur
        List<CommercialOffer> allOffers = offerRepository.findAll(
                availabilityService.currentlyAvailable(request.sector(), null, "FR"));

        List<ComparisonOfferResult> results = new ArrayList<>();
        int totalEligible = 0;

        for (CommercialOffer offer : allOffers) {
            OfferAvailabilityService.AvailabilityResult availability =
                    eligibilityService.evaluate(offer, "FR");
            if (!availability.available()) continue;
            totalEligible++;

            List<com.eparly.api.offer.domain.OfferEvidence> evidence =
                    new ArrayList<>(List.of(availability.evidence()));
            availabilityService.currentPromotionEvidence(offer).ifPresent(promotionEvidence -> {
                if (promotionService.isPromotionApplicable(
                        availability.pricing(), offer, availabilityService.currentDate(), promotionEvidence)) {
                    evidence.add(promotionEvidence);
                }
            });
            ComparisonOfferResult result = strategy.evaluateOffer(
                    request, offer, availability.pricing(), evidence);
            results.add(result);
        }

        // Trier par score heuristique décroissant
        results.sort((a, b) -> {
            Integer sa = a.heuristicScore() == null ? 0 : a.heuristicScore();
            Integer sb = b.heuristicScore() == null ? 0 : b.heuristicScore();
            return sb.compareTo(sa);
        });

        ComparisonResponse response = strategy.buildResponse(request, results);

        // Remplir les informations de l'abonnement analysé
        return new ComparisonResponse(
                subscription.getId(),
                subscription.getName(),
                subscription.getProvider(),
                response.sector(),
                response.appliedCriteria(),
                response.compatibleOffers(),
                response.incompatibleOffers(),
                response.needsInformationOffers(),
                response.notEligibleOffers(),
                totalEligible,
                response.catalogFreshnessAt(),
                response.catalogExhaustive(),
                response.commercialTransparencyNote(),
                Instant.now()
        );
    }

    public ComparisonStrategy validateRequest(ComparisonRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requête de comparaison invalide");
        }
        validator.validate(request, request.sector());
        ComparisonStrategy strategy = findStrategy(request.sector());
        validator.validate(request, request.sector(), strategy);
        return strategy;
    }

    /**
     * Retourne les critères disponibles pour un secteur.
     */
    public List<com.eparly.api.comparison.dto.ComparisonCriterionDetail> getCriteria(OfferSector sector) {
        ComparisonStrategy strategy = findStrategy(sector);
        return strategy.availableCriteria();
    }

    /**
     * Retourne les métadonnées du moteur.
     */
    public ComparisonMetadataResponse getMetadata() {
        List<OfferSector> sectors = new ArrayList<>();
        for (ComparisonStrategy s : strategies) {
            sectors.add(s.supportedSector());
        }
        return new ComparisonMetadataResponse(
                sectors,
                "1.0.0",
                "Comparaison déterministe des caractéristiques déclarées vs besoins utilisateur",
                "Offres vérifiées manuellement, publiquement consultables",
                "Score heuristique décroissant (pas une probabilité)",
                "Vérification de moins de " + availabilityService.freshnessThresholdDays()
                        + " jours. Une promotion exige une preuve dédiée de moins de "
                        + availabilityService.promotionFreshnessThresholdDays() + " jours.",
                "Le catalogue n'est pas exhaustif. Il référence uniquement les offres vérifiées.",
                "Aucune rémunération d'affiliation n'influence le classement."
        );
    }

    private ComparisonStrategy findStrategy(OfferSector sector) {
        for (ComparisonStrategy s : strategies) {
            if (s.supportedSector() == sector) return s;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Aucune stratégie de comparaison pour le secteur: " + sector);
    }
}
