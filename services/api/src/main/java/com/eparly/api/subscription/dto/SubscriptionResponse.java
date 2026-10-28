package com.eparly.api.subscription.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID accountId,
        String accountName,
        UUID recurringSeriesId,
        String name,
        String provider,
        Long declaredAmountMinor,
        String currency,
        RecurringFrequency declaredFrequency,
        SubscriptionStatus declaredStatus,
        SubscriptionOrigin origin,
        LocalDate nextDueDate,
        Instant confirmedAt,
        String notes,
        Instant statusChangedAt,
        Instant createdAt,
        Instant updatedAt,
        // observed from linked recurring series
        Long observedUsualAmountMinor,
        RecurringFrequency observedFrequency,
        LocalDate observedLastPaymentDate,
        LocalDate observedNextEstimatedDate,
        BigDecimal observedMonthlyEquivalentMinor,
        Integer observedConfidenceScore,
        // computed cost
        Long costAmountMinor,
        String costAmountSource,
        RecurringFrequency costFrequency,
        BigDecimal monthlyEquivalentMinor,
        BigDecimal annualEquivalentMinor,
        LocalDate lastObservedPaymentDate,
        LocalDate indicativeNextDueDate,
        boolean contradictoryObservation,
        // compatibility aliases
        SubscriptionStatus status,
        RecurringFrequency frequency,
        Long amountMinor,
        LocalDate lastPaymentDate,
        LocalDate nextEstimatedDate,
        Long usualAmountMinor
) {
}
