package com.eparly.api.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import com.eparly.api.recurring.domain.RecurringFrequency;

import java.time.LocalDate;

public record UpdateSubscriptionRequest(
        String name,
        String provider,
        Long declaredAmountMinor,
        String currency,
        RecurringFrequency declaredFrequency,
        LocalDate nextDueDate,
        String notes,
        LocalDate commitmentEndDate,
        Boolean noLongerNeeded,
        @Schema(description = "Informations a marquer comme inconnues : AMOUNT, FREQUENCY, NEXT_DUE_DATE, COMMITMENT_END_DATE, PROVIDER") List<String> unknownFields
) {
}
