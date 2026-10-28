package com.eparly.api.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Informations nécessaires pour créer un compte CSV")
public record CreateAccountRequest(
        @Schema(description = "Nom affiché du compte", example = "Compte courant", maxLength = 100)
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "Devise ISO 4217 ; EUR par défaut", example = "EUR", pattern = "^[A-Za-z]{3}$")
        @Pattern(regexp = "^[A-Za-z]{3}$") String currency
) {
}
