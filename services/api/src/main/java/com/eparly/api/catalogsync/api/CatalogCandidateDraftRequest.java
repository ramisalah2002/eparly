package com.eparly.api.catalogsync.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param evidenceUrl URL officielle du fournisseur, lorsque celle de la source n'appartient pas a son domaine
 */
public record CatalogCandidateDraftRequest(
        @NotNull UUID providerId,
        @Size(max = 200) String commercialName,
        @Size(max = 1000) String evidenceUrl,
        @Size(max = 100) String territory
) {
}
