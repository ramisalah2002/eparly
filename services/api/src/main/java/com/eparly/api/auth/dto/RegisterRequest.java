package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Informations nécessaires à la création d'un compte")
public record RegisterRequest(
        @Schema(example = "alex.martin@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "Secret123!", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "8 à 128 caractères")
        @NotBlank @Size(min = 8, max = 128) String password,
        @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Acceptation des conditions d'utilisation")
        boolean termsAccepted,
        @Schema(example = "false", description = "Consentement marketing facultatif")
        boolean marketingConsent
) {
}
