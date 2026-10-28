package com.eparly.api.comparison.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonCriterionDetail;
import com.eparly.api.comparison.dto.ComparisonMetadataResponse;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.dto.ComparisonResponse;
import com.eparly.api.comparison.strategy.ComparisonStrategy;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
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
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ComparisonService {

    private final SubscriptionRepository subscriptionRepository;
    private final CommercialOfferRepository offerRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferEvidenceRepository evidenceRepository;
    private final OfferEligibilityService eligibilityService;
    private final ComparisonCriteriaValidator validator;
    private final List<ComparisonStrategy> strategies;

    public ComparisonService(SubscriptionRepository subscriptionRepository,
                             CommercialOfferRepository offerRepository,
                             OfferPricingRepository pricingRepository,
                             OfferEvidenceRepository evidenceRepository,
                             OfferEligibilityService eligibilityService,
                             ComparisonCriteriaValidator validator,
                             List<ComparisonStrategy> strategies) {
        this.subscriptionRepository = subscriptionRepository;
        this.offerRepository = offerRepository;
        this.pricingRepository = pricingRepository;
        this.evidenceRepository = evidenceRepository;
        this.eligibilityService = eligibilityService;
        this.validator = validator;
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

        // Valider les critères selon le secteur
        ComparisonStrategy strategy = findStrategy(request.sector());
        validator.validate(request, request.sector());

        // Récupérer les offres publiquement consultables pour ce secteur
        LocalDate now = LocalDate.now();
        List<CommercialOffer> allOffers = offerRepository.findByPublicationStatus(
                com.eparly.api.offer.domain.OfferStatus.VERIFIED);

        List<ComparisonOfferResult> results = new ArrayList<>();
        int totalEligible = 0;

        for (CommercialOffer offer : allOffers) {
            if (!offer.getSector().equals(request.sector())) continue;
            if (!eligibilityService.isEligible(offer, "FR", now)) continue;
            totalEligible++;

            OfferPricing pricing = pricingRepository.findByOfferId(offer.getId()).orElse(null);
            List<OfferEvidence> evidence = evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(offer.getId());

            ComparisonOfferResult result = strategy.evaluateOffer(request, offer, pricing, evidence);
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
                "Vérification de moins de 30 jours (7 jours pour les promotions)",
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
