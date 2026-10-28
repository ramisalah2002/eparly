package com.eparly.api.comparison.service;

import com.eparly.api.comparison.config.ComparisonProperties;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class OfferEligibilityService {

    private final OfferEvidenceRepository evidenceRepository;
    private final OfferPricingRepository pricingRepository;
    private final ComparisonProperties properties;

    public OfferEligibilityService(OfferEvidenceRepository evidenceRepository,
                                   OfferPricingRepository pricingRepository,
                                   ComparisonProperties properties) {
        this.evidenceRepository = evidenceRepository;
        this.pricingRepository = pricingRepository;
        this.properties = properties;
    }

    /**
     * Verifie si une offre est publiable comme alternative dans un comparateur.
     * Une offre doit etre VERIFIED, non expiree, non stale, non archivee,
     * et posseder une evidence verifiee recente.
     */
    public boolean isEligible(CommercialOffer offer, String territory, LocalDate now) {
        if (offer == null) return false;
        if (offer.getPublicationStatus() != OfferStatus.VERIFIED) return false;
        if (offer.getValidUntil() != null && !offer.getValidUntil().isAfter(now)) return false;
        if (!territory.equalsIgnoreCase(offer.getTerritory())) return false;

        // Verifier qu'il existe au moins une evidence verifiee recente
        List<com.eparly.api.offer.domain.OfferEvidence> evidenceList =
                evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(offer.getId());
        if (evidenceList.isEmpty()) return false;

        com.eparly.api.offer.domain.OfferEvidence latest = evidenceList.get(0);
        if (latest.getVerificationStatus() != EvidenceStatus.VERIFIED) return false;

        Instant freshnessThreshold = Instant.now().minus(properties.getFreshnessThreshold());
        if (latest.getVerifiedAt() != null && latest.getVerifiedAt().isBefore(freshnessThreshold)) {
            return false;
        }

        // Verifier que le pricing existe et est fiable
        return pricingRepository.findByOfferId(offer.getId()).isPresent();
    }
}
