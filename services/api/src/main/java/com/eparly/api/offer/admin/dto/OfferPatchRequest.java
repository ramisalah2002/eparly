package com.eparly.api.offer.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OfferPatchRequest(
        @NotNull Long expectedVersion,
        @Size(min = 1, max = 200) String commercialName,
        @Size(max = 20000) String characteristics,
        @Size(max = 10000) String eligibilityConditions,
        @Size(max = 100) String commitment,
        @Size(min = 1, max = 100) String territory,
        LocalDate validFrom,
        LocalDate validUntil
) {
}
