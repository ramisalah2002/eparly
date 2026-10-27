package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Couple de jetons : access token court et refresh token opaque renouvelable")
public record TokenResponse(
        @Schema(example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                description = "JWT RS256 valable 15 minutes par défaut") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900", description = "Durée de vie de l'access token en secondes") long expiresIn,
        @Schema(example = "m7vY8jqk3aLGqYzvP2k8Y9U6jxE5vB7yS4xT1cD0eFg",
                description = "Secret opaque à stocker uniquement dans Expo SecureStore") String refreshToken,
        @Schema(example = "2592000", description = "Durée restante du refresh token en secondes") long refreshExpiresIn
) {
}
