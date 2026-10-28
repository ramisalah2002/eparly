package com.eparly.api.account.dto;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.domain.AccountSourceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Compte financier appartenant à l'utilisateur authentifié")
public record AccountResponse(
        UUID id,
        String name,
        AccountSourceType sourceType,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getSourceType(),
                account.getCurrency(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
