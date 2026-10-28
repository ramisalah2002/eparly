package com.eparly.api.comparison.dto;

import com.eparly.api.offer.domain.OfferSector;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

@Schema(description = "Requête d'évaluation de comparaison d'offres")
public record ComparisonRequest(
        @NotNull(message = "subscriptionId is required")
        UUID subscriptionId,

        @NotNull(message = "sector is required")
        OfferSector sector,

        @Size(max = 1000)
        Map<String, Object> requirements
) {
}
