package com.eparly.api.subscription.dto;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionSummaryResponse(
        int totalSubscriptions,
        List<CurrencySummary> currencies
) {
    public record CurrencySummary(
            String currency,
            int subscriptionCount,
            BigDecimal monthlyEquivalentMinor,
            BigDecimal annualEquivalentMinor,
            int toVerifyCount
    ) {
    }
}
