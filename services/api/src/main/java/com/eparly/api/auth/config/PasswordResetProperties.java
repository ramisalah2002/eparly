package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "eparly.security.password-reset")
public record PasswordResetProperties(
        @DefaultValue("PT30M") Duration ttl
) {
    public PasswordResetProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Password-reset duration must be positive");
        }
    }
}
