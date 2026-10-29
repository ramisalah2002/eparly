package com.eparly.api.savings.verification.dto;

import com.eparly.api.savings.verification.domain.EvidenceProvenance;
import com.eparly.api.savings.verification.domain.EvidenceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Synthèse d'un paiement bancaire observé rattaché à une action")
public record ObservedPaymentSummary(
        @Schema(description = "Identifiant de la preuve d'association")
        UUID evidenceId,

        @Schema(description = "Identifiant de la transaction")
        UUID transactionId,

        @Schema(description = "Date d'opération")
        LocalDate bookingDate,

        @Schema(description = "Montant de la transaction en centimes")
        Long amountMinor,

        @Schema(description = "Devise ISO 4217")
        String currency,

        @Schema(description = "Libellé de la transaction")
        String label,

        @Schema(description = "Rôle de la transaction")
        EvidenceRole role,

        @Schema(description = "Provenance du rapprochement")
        EvidenceProvenance provenance
) {
}
