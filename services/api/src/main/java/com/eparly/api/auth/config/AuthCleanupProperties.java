package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "eparly.security.cleanup")
public record AuthCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String cron,
        @DefaultValue("P7D") Duration emailTokenRetention,
        @DefaultValue("P7D") Duration passwordResetTokenRetention,
        @DefaultValue("P30D") Duration refreshTokenRetention
) {
}
