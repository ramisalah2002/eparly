package com.eparly.api.offer.dto;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Element de liste d'offre commerciale (vue legere pour pagination)")
public record OfferListItem(
        UUID id,
        UUID providerId,
        String providerName,
        String commercialName,
        OfferSector sector,
        String territory,
        OfferStatus publicationStatus,
        Long amountMinor,
        String currency,
        String billingPeriod,
        LocalDate validFrom,
        LocalDate validUntil,
        Instant createdAt,
        Instant updatedAt
) {
}
