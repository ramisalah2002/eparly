package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "eparly.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean useForwardedFor,
        List<String> trustedProxies,
        int loginIpMaxAttempts,
        Duration loginIpWindow,
        int loginAccountMaxAttempts,
        Duration loginAccountWindow,
        int registerIpMaxAttempts,
        Duration registerIpWindow,
        int resendVerificationIpMaxAttempts,
        Duration resendVerificationIpWindow,
        int resendVerificationEmailMaxAttempts,
        Duration resendVerificationEmailWindow,
        int forgotPasswordIpMaxAttempts,
        Duration forgotPasswordIpWindow,
        int forgotPasswordEmailMaxAttempts,
        Duration forgotPasswordEmailWindow,
        int resetPasswordIpMaxAttempts,
        Duration resetPasswordIpWindow,
        int refreshIpMaxAttempts,
        Duration refreshIpWindow
) {
    public RateLimitProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("false") boolean useForwardedFor,
            @DefaultValue("") List<String> trustedProxies,
            @DefaultValue("10") int loginIpMaxAttempts,
            @DefaultValue("PT15M") Duration loginIpWindow,
            @DefaultValue("5") int loginAccountMaxAttempts,
            @DefaultValue("PT15M") Duration loginAccountWindow,
            @DefaultValue("5") int registerIpMaxAttempts,
            @DefaultValue("PT1H") Duration registerIpWindow,
            @DefaultValue("5") int resendVerificationIpMaxAttempts,
            @DefaultValue("PT1H") Duration resendVerificationIpWindow,
            @DefaultValue("3") int resendVerificationEmailMaxAttempts,
            @DefaultValue("PT15M") Duration resendVerificationEmailWindow,
            @DefaultValue("5") int forgotPasswordIpMaxAttempts,
            @DefaultValue("PT1H") Duration forgotPasswordIpWindow,
            @DefaultValue("3") int forgotPasswordEmailMaxAttempts,
            @DefaultValue("PT15M") Duration forgotPasswordEmailWindow,
            @DefaultValue("10") int resetPasswordIpMaxAttempts,
            @DefaultValue("PT15M") Duration resetPasswordIpWindow,
            @DefaultValue("60") int refreshIpMaxAttempts,
            @DefaultValue("PT1M") Duration refreshIpWindow
    ) {
        this.enabled = enabled;
        this.useForwardedFor = useForwardedFor;
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
        this.loginIpMaxAttempts = loginIpMaxAttempts;
        this.loginIpWindow = loginIpWindow;
        this.loginAccountMaxAttempts = loginAccountMaxAttempts;
        this.loginAccountWindow = loginAccountWindow;
        this.registerIpMaxAttempts = registerIpMaxAttempts;
        this.registerIpWindow = registerIpWindow;
        this.resendVerificationIpMaxAttempts = resendVerificationIpMaxAttempts;
        this.resendVerificationIpWindow = resendVerificationIpWindow;
        this.resendVerificationEmailMaxAttempts = resendVerificationEmailMaxAttempts;
        this.resendVerificationEmailWindow = resendVerificationEmailWindow;
        this.forgotPasswordIpMaxAttempts = forgotPasswordIpMaxAttempts;
        this.forgotPasswordIpWindow = forgotPasswordIpWindow;
        this.forgotPasswordEmailMaxAttempts = forgotPasswordEmailMaxAttempts;
        this.forgotPasswordEmailWindow = forgotPasswordEmailWindow;
        this.resetPasswordIpMaxAttempts = resetPasswordIpMaxAttempts;
        this.resetPasswordIpWindow = resetPasswordIpWindow;
        this.refreshIpMaxAttempts = refreshIpMaxAttempts;
        this.refreshIpWindow = refreshIpWindow;
    }
}
