package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Identifiants de connexion")
public record LoginRequest(
        @Schema(example = "alex.martin@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "Correct-Horse-Battery-42!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String password
) {
}
