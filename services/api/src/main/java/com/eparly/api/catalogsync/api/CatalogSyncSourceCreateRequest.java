package com.eparly.api.catalogsync.api;

import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.offer.domain.OfferSector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CatalogSyncSourceCreateRequest(
        @NotBlank @Size(max = 200) String sourceName,
        @NotBlank @Size(max = 253) String officialDomain,
        @NotBlank @Size(max = 2000) String sourceUrl,
        @NotNull CatalogSourceType sourceType,
        @NotNull OfferSector sector,
        @Pattern(regexp = "^[A-Z]{2}$") String country,
        UUID providerId,
        @Size(max = 10000) String extractorConfig,
        @Size(max = 60) String connectorKey
) {
}
