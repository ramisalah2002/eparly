package com.eparly.api.catalogsync.domain;

import java.time.Instant;
import java.util.UUID;

public record CatalogSyncSourceReport(
        UUID sourceId,
        String sourceName,
        String status,
        Instant startedAt,
        Instant finishedAt,
        int pagesFetched,
        int offersCollected,
        int offersDiscovered,
        int offersUpdated,
        int offersUnchanged,
        int offersDuplicates,
        int offersRejected,
        int offersNotReturned,
        String errorCode,
        String errorMessage
) {
}
