package com.eparly.api.recurring.dto;

import java.time.Instant;

public record RecurringAnalyzeResponse(
        int analyzedAccounts,
        int transactionsAnalyzed,
        int seriesCreated,
        int seriesUpdated,
        int candidatesCount,
        Instant analyzedAt,
        String message,
        int created,
        int updated,
        int candidates,
        int transactions,
        int accounts
) {}
