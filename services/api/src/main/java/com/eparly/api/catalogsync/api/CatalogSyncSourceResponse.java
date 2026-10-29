package com.eparly.api.catalogsync.api;

import java.time.Instant;
import java.util.UUID;

public record CatalogSyncSourceResponse(
        UUID id,
        UUID providerId,
        String sourceName,
        String officialDomain,
        String sourceUrl,
        String sourceType,
        String sector,
        String country,
        boolean collectionEnabled,
        String permissionStatus,
        String permissionReason,
        String robotsStatus,
        Instant lastCheckedAt,
        Instant lastSuccessAt,
        String lastError
) {
}
