package com.eparly.api.subscription.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;

import java.time.LocalDate;

public record UpdateSubscriptionRequest(
        String name,
        String provider,
        Long declaredAmountMinor,
        String currency,
        RecurringFrequency declaredFrequency,
        LocalDate nextDueDate,
        String notes
) {
}
