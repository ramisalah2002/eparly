package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Réinitialisation du mot de passe avec jeton de validation")
public record ResetPasswordRequest(
        @Schema(example = "dGhpcy1pcy1hLXNhbXBsZS10b2tlbi12YWx1ZQ", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Jeton de réinitialisation à usage unique")
        @NotBlank String token,
        @Schema(example = "Secret123!", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Nouveau mot de passe (8 à 128 caractères)")
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
}
