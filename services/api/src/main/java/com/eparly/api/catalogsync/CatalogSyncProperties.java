package com.eparly.api.catalogsync;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eparly.catalog-sync")
public class CatalogSyncProperties {
    private boolean enabled;
    private String cron = "0 0 4 * * *";
    private String zone = "Europe/Paris";
    private String userAgent = "EparlyCatalogBot/1.0";
    private String contact = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(8);
    private Duration requestDelay = Duration.ofSeconds(1);
    private int maxResponseBytes = 2 * 1024 * 1024;
    private int maxPagesPerSource = 30;
    private int maxRedirects = 3;
    private boolean allowLoopbackForTests;

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
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public int getMaxPagesPerSource() { return maxPagesPerSource; }
    public void setMaxPagesPerSource(int maxPagesPerSource) { this.maxPagesPerSource = maxPagesPerSource; }
    public int getMaxRedirects() { return maxRedirects; }
    public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
    public boolean isAllowLoopbackForTests() { return allowLoopbackForTests; }
    public void setAllowLoopbackForTests(boolean allowLoopbackForTests) {
        this.allowLoopbackForTests = allowLoopbackForTests;
    }
}
