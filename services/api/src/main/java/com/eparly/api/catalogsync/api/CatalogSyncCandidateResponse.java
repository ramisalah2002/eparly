package com.eparly.api.catalogsync.api;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record CatalogSyncCandidateResponse(
        UUID id,
        UUID providerId,
        UUID existingOfferId,
        UUID observationId,
        String candidateStatus,
        String changeType,
        Instant detectedAt,
        String validationStatus,
        double confidence,
        JsonNode normalizedData,
        JsonNode validationReasons
) {
}
