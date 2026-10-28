package com.eparly.api.recurring.dto;

import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RecurringDecisionRequest(
        RecurringSeriesStatus status,
        @JsonAlias({"type", "series_type", "recurringType"}) RecurringSeriesType seriesType,
        @JsonAlias({"comment", "reason"}) String note
) {}
