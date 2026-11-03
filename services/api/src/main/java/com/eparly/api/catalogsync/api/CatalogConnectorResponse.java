package com.eparly.api.catalogsync.api;

public record CatalogConnectorResponse(String key, boolean ready, String reason) {
}
