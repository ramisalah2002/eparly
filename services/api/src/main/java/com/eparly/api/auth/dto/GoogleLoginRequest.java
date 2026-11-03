package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Connexion ou inscription avec un Google ID Token")
public record GoogleLoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Google ID Token (JWT), pas un Access Token")
        @NotBlank @Size(max = 8192) String idToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Identifiant du challenge LOGIN")
        @NotNull UUID challengeId,
        @Schema(description = "Acceptation des conditions d'utilisation ; obligatoire pour creer un compte")
        Boolean termsAccepted,
        @Schema(description = "Consentement marketing facultatif")
        Boolean marketingConsent
) {
    @Override
    public String toString() {
        return "GoogleLoginRequest[idToken=***, challengeId=" + challengeId + "]";
    }
}
