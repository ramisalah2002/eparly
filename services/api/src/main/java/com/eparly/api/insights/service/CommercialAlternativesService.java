package com.eparly.api.insights.service;

import com.eparly.api.insights.domain.InsightModels.AlternativesStatus;
import com.eparly.api.insights.dto.InsightDtos.CommercialAlternatives;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferReuseRights;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.service.OfferAvailabilityService;
import org.springframework.stereotype.Service;

import java.util.List;

/** Offres reellement publiees et disponibles, droits etablis. Les brouillons ne comptent jamais. */
@Service
public class CommercialAlternativesService {
    private final CommercialOfferRepository offerRepository;
    private final OfferAvailabilityService availabilityService;

    public CommercialAlternativesService(CommercialOfferRepository offerRepository, OfferAvailabilityService availabilityService) {
        this.offerRepository = offerRepository;
        this.availabilityService = availabilityService;
    }

    public CommercialAlternatives current() {
        List<CommercialOffer> published = offerRepository.findAll((root, query, builder) -> builder.and(
                builder.equal(root.get("publicationStatus"), OfferStatus.VERIFIED),
                builder.equal(root.get("reuseRightsStatus"), OfferReuseRights.ESTABLISHED)));
        int available = (int) published.stream()
                .filter(offer -> availabilityService.evaluate(offer, offer.getTerritory()).available()).count();
        return available == 0
                ? new CommercialAlternatives(AlternativesStatus.NONE_AVAILABLE, 0, "Aucune alternative fiable n'est actuellement proposée.")
                : new CommercialAlternatives(AlternativesStatus.AVAILABLE_FOR_COMPARISON, available,
                "Des offres vérifiées peuvent être comparées via l'API de comparaison.");
    }
}
