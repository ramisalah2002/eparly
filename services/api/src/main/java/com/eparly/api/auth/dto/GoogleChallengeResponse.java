package com.eparly.api.auth.dto;

import com.eparly.api.auth.domain.GoogleChallengePurpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Challenge a renvoyer avec le Google ID Token")
public record GoogleChallengeResponse(
        @Schema(description = "Identifiant a renvoyer avec le Google ID Token") UUID challengeId,
        @Schema(description = "Nonce a transmettre tel quel au SDK Google") String nonce,
        GoogleChallengePurpose purpose,
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "GoogleChallengeResponse[challengeId=" + challengeId + ", nonce=***]";
    }
}
