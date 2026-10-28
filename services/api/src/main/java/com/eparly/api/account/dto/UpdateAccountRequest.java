package com.eparly.api.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Champs modifiables d'un compte")
public record UpdateAccountRequest(
        @Schema(description = "Nouveau nom affiché", example = "Compte principal", maxLength = 100)
        @Size(max = 100) String name,
        @Schema(description = "Nouvelle devise ISO 4217 ; modifiable uniquement sans transaction", example = "EUR")
        @Pattern(regexp = "^[A-Za-z]{3}$") String currency
) {
}
