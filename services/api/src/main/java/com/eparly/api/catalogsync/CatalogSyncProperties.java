package com.eparly.api.catalogsync;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eparly.catalog-sync")
public class CatalogSyncProperties {
    private boolean enabled;
    private String cron = "0 0 3 * * *";
    private String zone = "Europe/Paris";
    private String userAgent = "EparlyCatalogBot/1.0";
    private String contact = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(8);
    private Duration requestDelay = Duration.ofSeconds(1);
    private Duration maxExecutionDuration = Duration.ofMinutes(10);
    private int maxResponseBytes = 2 * 1024 * 1024;
    private int maxPagesPerSource = 30;
    private int maxRedirects = 3;
    private int maxRetries = 2;
    private boolean allowLoopbackForTests;
    private boolean businessIngestionEnabled;
    private boolean catchUpOnStartup;
    private Duration failureRetryDelay = Duration.ofMinutes(30);
    private int maxParallelSources = 2;
    private int circuitBreakerFailureThreshold = 5;
    private Duration circuitBreakerCooldown = Duration.ofDays(1);
    private int maxRawPayloadBytes = 8192;
    private final Tradedoubler tradedoubler = new Tradedoubler();

    /** Le jeton provient exclusivement de l'environnement ; il n'est jamais persiste ni journalise. */
    public static class Tradedoubler {
        private boolean enabled;
        private String token = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        @Override
        public String toString() {
            return "Tradedoubler{enabled=" + enabled + "}";
        }
    }

    public boolean isBusinessIngestionEnabled() { return businessIngestionEnabled; }
    public void setBusinessIngestionEnabled(boolean value) { this.businessIngestionEnabled = value; }
    public boolean isCatchUpOnStartup() { return catchUpOnStartup; }
    public void setCatchUpOnStartup(boolean value) { this.catchUpOnStartup = value; }
    public Duration getFailureRetryDelay() { return failureRetryDelay; }
    public void setFailureRetryDelay(Duration value) { this.failureRetryDelay = value; }
    public int getMaxParallelSources() { return maxParallelSources; }
    public void setMaxParallelSources(int maxParallelSources) { this.maxParallelSources = maxParallelSources; }
    public int getCircuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public void setCircuitBreakerFailureThreshold(int threshold) { this.circuitBreakerFailureThreshold = threshold; }
    public Duration getCircuitBreakerCooldown() { return circuitBreakerCooldown; }
    public void setCircuitBreakerCooldown(Duration cooldown) { this.circuitBreakerCooldown = cooldown; }
    public int getMaxRawPayloadBytes() { return maxRawPayloadBytes; }
    public void setMaxRawPayloadBytes(int maxRawPayloadBytes) { this.maxRawPayloadBytes = maxRawPayloadBytes; }
    public Tradedoubler getTradedoubler() { return tradedoubler; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public Duration getRequestDelay() { return requestDelay; }
    public void setRequestDelay(Duration requestDelay) { this.requestDelay = requestDelay; }
    public Duration getMaxExecutionDuration() { return maxExecutionDuration; }
    public void setMaxExecutionDuration(Duration maxExecutionDuration) {
        this.maxExecutionDuration = maxExecutionDuration;
    }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public int getMaxPagesPerSource() { return maxPagesPerSource; }
    public void setMaxPagesPerSource(int maxPagesPerSource) { this.maxPagesPerSource = maxPagesPerSource; }
    public int getMaxRedirects() { return maxRedirects; }
    public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public boolean isAllowLoopbackForTests() { return allowLoopbackForTests; }
    public void setAllowLoopbackForTests(boolean allowLoopbackForTests) {
        this.allowLoopbackForTests = allowLoopbackForTests;
    }
}
