package com.eparly.api.offer.dto;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.ProviderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Fournisseur d'offre commerciale du catalogue")
public record OfferProviderResponse(
        UUID id,
        String name,
        OfferSector sector,
        String officialWebsite,
        String country,
        ProviderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
