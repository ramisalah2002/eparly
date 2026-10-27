package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Demande d'envoi d'un jeton de réinitialisation de mot de passe")
public record ForgotPasswordRequest(
        @Schema(example = "alex.martin@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255) String email
) {
}
