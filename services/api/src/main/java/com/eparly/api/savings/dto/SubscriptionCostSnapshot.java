package com.eparly.api.savings.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Snapshot du coût actuel d'un abonnement")
public record SubscriptionCostSnapshot(

        UUID subscriptionId,
        String subscriptionName,
        String currentProvider,
        Long amountMinor,
        CostSource costSource,
        RecurringFrequency frequency,
        String currency,
        SubscriptionStatus declaredStatus,
        SubscriptionOrigin origin,
        LocalDate nextDueDate,
        Instant confirmedAt,
        Instant statusChangedAt
) {
    public static SubscriptionCostSnapshot canceled(
            UUID id, String name, String provider,
            String currency, SubscriptionOrigin origin,
            Instant statusChangedAt
    ) {
        return new SubscriptionCostSnapshot(
                id, name, provider, null, CostSource.UNKNOWN, null,
                currency, SubscriptionStatus.CANCELED, origin,
                null, null, statusChangedAt
        );
    }

    public static SubscriptionCostSnapshot paused(
            UUID id, String name, String provider,
            Long amountMinor, CostSource costSource, RecurringFrequency frequency,
            String currency, SubscriptionOrigin origin,
            LocalDate nextDueDate, Instant statusChangedAt
    ) {
        return new SubscriptionCostSnapshot(
                id, name, provider, amountMinor, costSource, frequency,
                currency, SubscriptionStatus.PAUSED, origin,
                nextDueDate, null, statusChangedAt
        );
    }

    public boolean hasUsableCost() {
        return amountMinor != null && amountMinor >= 0 && frequency != null
                && frequency != RecurringFrequency.UNKNOWN;
    }
}
