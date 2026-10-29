package com.eparly.api.savings.verification.dto;

import com.eparly.api.savings.verification.domain.EvidenceProvenance;
import com.eparly.api.savings.verification.domain.EvidenceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Preuve de transaction bancaire associée à une action d'économie")
public record EvidenceResponse(
        @Schema(description = "Identifiant de la preuve")
        UUID id,

        @Schema(description = "Identifiant de l'action d'économie concernée")
        UUID actionId,

        @Schema(description = "Identifiant de la transaction bancaire sous-jacente")
        UUID transactionId,

        @Schema(description = "Rôle de la preuve")
        EvidenceRole role,

        @Schema(description = "Provenance de l'association")
        EvidenceProvenance provenance,

        @Schema(description = "Date et heure de confirmation de l'association")
        Instant confirmedAt,

        @Schema(description = "Montant de la transaction en centimes")
        Long amountMinor,

        @Schema(description = "Devise ISO 4217")
        String currency,

        @Schema(description = "Date d'opération bancaire")
        LocalDate bookingDate,

        @Schema(description = "Libellé de la transaction")
        String label
) {
}
