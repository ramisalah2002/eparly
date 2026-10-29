package com.eparly.api.catalogsync.domain;

import java.time.Instant;
import java.util.UUID;

public record CatalogSyncReport(
        UUID id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        int sourcesConfigured,
        int sourcesProcessed,
        int pagesFetched,
        int pagesUnsupported,
        int offersDiscovered,
        int offersUpdated,
        int offersUnchanged,
        int offersRejected,
        int errorsCount
) {
}
