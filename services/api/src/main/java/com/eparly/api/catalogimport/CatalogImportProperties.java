package com.eparly.api.catalogimport;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "eparly.catalog-import")
public class CatalogImportProperties {
    private boolean enabled;
    private Path file;
    private boolean apply;
    private String actorUserId;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Path getFile() { return file; }
    public void setFile(Path file) { this.file = file; }
    public boolean isApply() { return apply; }
    public void setApply(boolean apply) { this.apply = apply; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
}