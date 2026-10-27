package com.eparly.api.auth.service;

import com.eparly.api.user.domain.User;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class UserSecurityValidator {
    private final UserRepository userRepository;

    public UserSecurityValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void validate(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Invalid subject in token", null));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token", "User does not exist", null)));

        // 1. Contrôle prioritaire par version de sécurité (ver claim)
        Object verClaim = jwt.getClaims().get("ver");
        if (verClaim instanceof Number number) {
            if (number.intValue() < user.getTokenVersion()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token", "Access token has been revoked", null));
            }
            return;
        }

        // 2. Contrôle de repli temporel (pour tokens sans claim "ver", ex: aides de test)
        Instant tokenInvalidBefore = user.getTokenInvalidBefore();
        if (tokenInvalidBefore != null) {
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt != null && !issuedAt.isAfter(tokenInvalidBefore)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token", "Access token has been revoked", null));
            }
        }
    }
}
