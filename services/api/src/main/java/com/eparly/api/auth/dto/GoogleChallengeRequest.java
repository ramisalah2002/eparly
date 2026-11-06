package com.eparly.api.auth.dto;

import com.eparly.api.auth.domain.GoogleChallengePurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Demande d'un challenge Google a usage unique")
public record GoogleChallengeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "LOGIN, ou LINK avec une session Eparly")
        @NotNull GoogleChallengePurpose purpose
) {
}
