package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eparly.security.refresh-token")
public record RefreshTokenProperties(Duration ttl) {
    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Refresh-token duration must be positive");
        }
    }
}
