package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Liaison d'un compte Google au compte Eparly connecte")
public record GoogleLinkRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Google ID Token (JWT)")
        @NotBlank @Size(max = 8192) String idToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Identifiant du challenge LINK de cet utilisateur")
        @NotNull UUID challengeId,
        @Schema(description = "Mot de passe actuel : preuve recente de controle, obligatoire si le compte en possede un")
        @Size(max = 128) String currentPassword
) {
    @Override
    public String toString() {
        return "GoogleLinkRequest[idToken=***, challengeId=" + challengeId + ", currentPassword=***]";
    }
}
