package com.eparly.api.auth.dto;

import com.eparly.api.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Profil de l'utilisateur authentifié")
public record UserProfileResponse(
        @Schema(example = "1b6e2b2e-6f4a-4a2f-9f4f-1a2b3c4d5e6f") UUID id,
        @Schema(example = "alex.martin@example.com") String email,
        @Schema(example = "Alex Martin") String fullName,
        @Schema(example = "ACTIVE") UserStatus status,
        Instant emailVerifiedAt,
        Instant createdAt,
        ProfileSettings settings
) {
    @Schema(description = "Préférences non sensibles du compte")
    public record ProfileSettings(
            @Schema(example = "EUR") String currency,
            @Schema(example = "FR") String country,
            @Schema(example = "Europe/Paris") String timezone
    ) {
    }
}
