package com.eparly.api.offer.dto;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detail complet d'une offre commerciale avec tarification, preuves et fournisseur")
public record CommercialOfferResponse(
        UUID id,
        OfferProviderResponse provider,
        String commercialName,
        OfferSector sector,
        String characteristics,
        String eligibilityConditions,
        String commitment,
        String territory,
        OfferStatus publicationStatus,
        LocalDate validFrom,
        LocalDate validUntil,
        OfferPricingResponse pricing,
        List<OfferEvidenceResponse> evidence,
        Instant createdAt,
        Instant updatedAt
) {
}
