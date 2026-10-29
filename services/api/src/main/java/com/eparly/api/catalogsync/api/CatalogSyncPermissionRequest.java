package com.eparly.api.catalogsync.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CatalogSyncPermissionRequest(
        @NotNull PermissionStatus permissionStatus,
        boolean collectionEnabled,
        @NotBlank @Size(min = 10, max = 1000) String reason
) {
    public enum PermissionStatus {
        PENDING,
        AUTHORIZED,
        DENIED
    }
}
