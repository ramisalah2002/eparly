package com.eparly.api.savings.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Requête de réévaluation du niveau de preuve d'une action d'économie")
public record EvaluateVerificationRequest(
        @Schema(description = "Date effective du changement déclarée explicitement par l'utilisateur (optionnel)")
        LocalDate effectiveChangeDate
) {
}
