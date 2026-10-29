package com.eparly.api.offer.admin.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OfferPriceHistoryResponse(
        UUID id,
        Long oldAmountMinor,
        Long newAmountMinor,
        Map<String, Object> oldPricingSnapshot,
        Map<String, Object> newPricingSnapshot,
        Instant changedAt,
        String reason
) {
}
