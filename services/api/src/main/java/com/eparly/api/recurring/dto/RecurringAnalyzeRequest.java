package com.eparly.api.recurring.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RecurringAnalyzeRequest(
        @JsonAlias({"accounts", "account_ids"}) List<UUID> accountIds,
        @JsonAlias({"forced"}) Boolean force
) {}
