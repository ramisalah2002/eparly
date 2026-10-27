package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Refresh token opaque envoyé dans le corps JSON")
public record RefreshTokenRequest(
        @Schema(example = "m7vY8jqk3aLGqYzvP2k8Y9U6jxE5vB7yS4xT1cD0eFg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String refreshToken
) {
}
