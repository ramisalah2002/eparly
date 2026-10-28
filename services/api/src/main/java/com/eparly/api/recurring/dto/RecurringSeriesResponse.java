package com.eparly.api.recurring.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringSeriesResponse(
        UUID id,
        UUID accountId,
        String accountName,
        String label,
        String currency,
        RecurringFrequency frequency,
        RecurringSeriesStatus status,
        RecurringSeriesType seriesType,
        long usualAmountMinor,
        long minAmountMinor,
        long maxAmountMinor,
        int occurrenceCount,
        LocalDate firstOccurrenceDate,
        LocalDate lastOccurrenceDate,
        LocalDate nextEstimatedDate,
        BigDecimal monthlyEquivalentMinor,
        int confidenceScore,
        String confidenceLevel,
        String normalizedLabel,
        com.eparly.api.recurring.domain.RecurringSeriesType type,
        int score,
        String confidence
) {}
