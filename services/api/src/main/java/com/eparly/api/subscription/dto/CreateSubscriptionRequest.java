package com.eparly.api.subscription.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSubscriptionRequest(
        UUID accountId,
        UUID recurringSeriesId,
        String name,
        String provider,
        Long declaredAmountMinor,
        String currency,
        RecurringFrequency declaredFrequency,
        LocalDate nextDueDate,
        SubscriptionStatus declaredStatus,
        String notes
) {
}
