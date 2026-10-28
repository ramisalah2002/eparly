package com.eparly.api.transaction.dto;

import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Transaction importée ; aucune donnée de propriétaire ou de traitement interne")
public record TransactionResponse(
        UUID id,
        UUID accountId,
        LocalDate bookingDate,
        LocalDate valueDate,
        Long amountMinor,
        String currency,
        String rawLabel,
        String normalizedLabel,
        TransactionType transactionType,
        UUID categoryId,
        boolean customCategoryFlag
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getBookingDate(),
                transaction.getValueDate(),
                transaction.getAmountMinor(),
                transaction.getCurrency(),
                transaction.getRawLabel(),
                transaction.getNormalizedLabel(),
                transaction.getTransactionType(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.isCustomCategoryFlag()
        );
    }
}
