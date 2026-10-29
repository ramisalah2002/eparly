package com.eparly.api.catalogsync.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CatalogSyncCandidateReviewRequest(
        @NotNull ReviewStatus status,
        UUID publishedOfferId
) {
    public enum ReviewStatus {
        ACCEPTED,
        REJECTED
    }
}
