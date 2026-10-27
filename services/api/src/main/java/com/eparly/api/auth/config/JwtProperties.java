package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eparly.security.jwt")
public record JwtProperties(String issuer, String audience, Duration accessTokenTtl) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("JWT access-token duration must be between 0 and 1 hour");
        }
    }
}
