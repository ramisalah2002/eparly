package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        RefreshTokenProperties.class,
        PasswordResetProperties.class,
        RateLimitProperties.class,
        AuthCleanupProperties.class
})
public class AuthPropertiesConfiguration {
}
