package com.eparly.api.recurring.dto;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecurringOccurrenceResponse(
        UUID transactionId,
        LocalDate bookingDate,
        long amountMinor,
        String currency,
        String rawLabel,
        String transactionType
) {}
