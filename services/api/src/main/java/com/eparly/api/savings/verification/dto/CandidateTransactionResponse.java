package com.eparly.api.savings.verification.dto;

import com.eparly.api.savings.verification.domain.EvidenceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Transaction candidate pouvant servir de preuve de paiement d'ancien ou de nouvel abonnement")
public record CandidateTransactionResponse(
        @Schema(description = "Identifiant de la transaction")
        UUID transactionId,

        @Schema(description = "Date d'opération bancaire")
        LocalDate bookingDate,

        @Schema(description = "Montant signé en centimes (négatif pour un débit)")
        Long amountMinor,

        @Schema(description = "Devise ISO 4217")
        String currency,

        @Schema(description = "Libellé brut bancaire")
        String rawLabel,

        @Schema(description = "Libellé normalisé")
        String normalizedLabel,

        @Schema(description = "Identifiant du compte bancaire")
        UUID accountId,

        @Schema(description = "Rôle suggéré par l'heuristique")
        EvidenceRole suggestedRole,

        @Schema(description = "Raisons explicatives du rapprochement")
        List<String> matchingReasons,

        @Schema(description = "Indice de confiance heuristique (0 à 100)")
        int confidenceScore
) {
}
