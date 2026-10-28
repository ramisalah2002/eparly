package com.eparly.api.recurring.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecurringSeriesDetailResponse(
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
        List<String> reasons,
        List<RecurringOccurrenceResponse> occurrences,
        Instant lastAnalyzedAt,
        Instant userDecisionAt,
        String decisionNote,
        String normalizedLabel,
        com.eparly.api.recurring.domain.RecurringSeriesType type,
        int score,
        String confidence,
        List<String> detectionReasons
) {}
