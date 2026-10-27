package com.eparly.api.auth.api;

import com.eparly.api.auth.dto.UserProfileResponse;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserSettings;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me")
@Tag(name = "Profile", description = "Profil de l'utilisateur connecté")
public class MeController {
    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Consulter son profil",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur authentifié")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré",
            content = @Content(schema = @Schema(implementation = com.eparly.api.auth.dto.ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Compte suspendu ou désactivé",
            content = @Content(schema = @Schema(implementation = com.eparly.api.auth.dto.ApiErrorResponse.class)))
    public UserProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
        User user = userRepository.findById(userId)
                .filter(value -> value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
        UserSettings settings = user.getSettings();
        UserProfileResponse.ProfileSettings profileSettings = settings == null ? null
                : new UserProfileResponse.ProfileSettings(
                        settings.getCurrency(), settings.getCountry(), settings.getTimezone()
                );
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getStatus(),
                user.getEmailVerifiedAt(), user.getCreatedAt(), profileSettings
        );
    }
}
