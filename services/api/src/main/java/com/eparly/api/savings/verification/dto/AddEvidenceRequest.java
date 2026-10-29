package com.eparly.api.savings.verification.dto;

import com.eparly.api.savings.verification.domain.EvidenceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Requête d'association d'une transaction bancaire comme élément de preuve")
public record AddEvidenceRequest(
        @NotNull(message = "transactionId est obligatoire")
        @Schema(description = "Identifiant de la transaction bancaire à associer")
        UUID transactionId,

        @NotNull(message = "role est obligatoire")
        @Schema(description = "Rôle de la transaction : OLD_SUBSCRIPTION_PAYMENT ou NEW_SUBSCRIPTION_PAYMENT")
        EvidenceRole role,

        @Schema(description = "Date effective du changement déclarée explicitement par l'utilisateur (optionnel)")
        LocalDate effectiveChangeDate
) {
}
