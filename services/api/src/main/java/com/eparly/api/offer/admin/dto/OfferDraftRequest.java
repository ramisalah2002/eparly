package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.OfferSector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record OfferDraftRequest(
        @NotNull UUID providerId,
        @NotBlank @Size(max = 200) String commercialName,
        @NotNull OfferSector sector,
        @Size(max = 20000) String characteristics,
        @Size(max = 10000) String eligibilityConditions,
        @Size(max = 100) String commitment,
        @NotBlank @Size(max = 100) String territory,
        LocalDate validFrom,
        LocalDate validUntil
) {
}
