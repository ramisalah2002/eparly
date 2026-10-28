package com.eparly.api.savings.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.comparison.dto.ComparisonOfferResult;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.savings.domain.SavingsStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Valide l'éligibilité d'une offre à une estimation d'économies.
 * Ne fait pas confiance au statut COMPATIBLE transmis par le client.
 * Recalcule ou réutilise de manière sûre l'évaluation réelle.
 */
@Service
public class SavingsEligibilityValidator {

    private final CommercialOfferRepository offerRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferEvidenceRepository evidenceRepository;

    public SavingsEligibilityValidator(
            CommercialOfferRepository offerRepository,
            OfferPricingRepository pricingRepository,
            OfferEvidenceRepository evidenceRepository) {
        this.offerRepository = offerRepository;
        this.pricingRepository = pricingRepository;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * Valide qu'une offre existe, est vérifiée, non expirée et éligible à une estimation.
     * Retourne l'offre et son pricing si tout est valide.
     */
    public ValidatedOffer validateOffer(UUID offerId, String territory, LocalDate now) {
        CommercialOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));

        if (offer.getPublicationStatus() != OfferStatus.VERIFIED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Offre non vérifiée (statut: " + offer.getPublicationStatus() + ")");
        }

        if (offer.getValidUntil() != null && !offer.getValidUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offre expirée");
        }

        if (!territory.equalsIgnoreCase(offer.getTerritory())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offre non disponible dans ce territoire");
        }

        // Vérifier l'évidence vérifiée récente
        List<com.eparly.api.offer.domain.OfferEvidence> evidenceList =
                evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(offerId);
        if (evidenceList.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aucune preuve de vérification pour cette offre");
        }

        com.eparly.api.offer.domain.OfferEvidence latest = evidenceList.get(0);
        if (latest.getVerificationStatus() != EvidenceStatus.VERIFIED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Preuve de vérification non valide");
        }

        Instant freshnessThreshold = Instant.now().minus(java.time.Duration.ofDays(30));
        if (latest.getVerifiedAt() != null && latest.getVerifiedAt().isBefore(freshnessThreshold)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offre obsolète (vérification trop ancienne)");
        }

        // Vérifier le pricing
        OfferPricing pricing = pricingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Tarification manquante pour cette offre"));

        if (pricing.getAmountMinor() == null || pricing.getAmountMinor() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Montant de l'offre invalide");
        }

        if (pricing.getBillingPeriod() == null || pricing.getBillingPeriod() == OfferBillingPeriod.UNKNOWN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Périodicité de facturation inconnue");
        }

        // Vérifier la devise (MVP: uniquement EUR)
        if (!"EUR".equalsIgnoreCase(pricing.getCurrency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Devise non supportée: " + pricing.getCurrency());
        }

        return new ValidatedOffer(offer, pricing, latest);
    }

    /**
     * Valide que le statut de compatibilité de l'offre permet une estimation d'économies.
     * Seules les offres COMPATIBLE sont éligibles.
     */
    public void validateCompatibility(ComparisonOfferResult comparisonResult) {
        if (comparisonResult == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Résultat de comparaison manquant");
        }
        if (comparisonResult.status() == ComparisonStatus.INCOMPATIBLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Offre incompatible avec les critères de l'utilisateur");
        }
        if (comparisonResult.status() == ComparisonStatus.NOT_ELIGIBLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Utilisateur non éligible pour cette offre");
        }
        if (comparisonResult.status() == ComparisonStatus.NEEDS_INFORMATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Informations insuffisantes pour établir l'équivalence");
        }
    }

    public record ValidatedOffer(
            CommercialOffer offer,
            OfferPricing pricing,
            com.eparly.api.offer.domain.OfferEvidence evidence
    ) {
    }
}
