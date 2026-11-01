package com.eparly.api.comparison.service;

import com.eparly.api.offer.domain.CommercialOffer;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class OfferEligibilityService {

    private final com.eparly.api.offer.service.OfferAvailabilityService availabilityService;

    public OfferEligibilityService(com.eparly.api.offer.service.OfferAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    /**
     * Verifie si une offre est publiable comme alternative dans un comparateur.
     * Une offre doit etre VERIFIED, non expiree, non stale, non archivee,
     * et posseder une evidence verifiee recente.
     */
    public boolean isEligible(CommercialOffer offer, String territory, LocalDate now) {
        return evaluate(offer, territory).available();
    }

    public com.eparly.api.offer.service.OfferAvailabilityService.AvailabilityResult evaluate(
            CommercialOffer offer, String territory) {
        return availabilityService.evaluate(offer, territory);
    }
}
