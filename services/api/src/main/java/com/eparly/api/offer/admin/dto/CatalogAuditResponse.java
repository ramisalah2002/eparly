package com.eparly.api.offer.admin.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CatalogAuditResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String resourceType,
        UUID resourceId,
        Map<String, Object> details,
        Instant occurredAt
) {
}
