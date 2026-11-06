package com.eparly.api.catalogsync.domain;

import com.eparly.api.offer.domain.OfferSector;

import java.util.UUID;

public record CatalogSyncSource(
        UUID id,
        UUID providerId,
        String sourceName,
        String officialDomain,
        String sourceUrl,
        CatalogSourceType sourceType,
        OfferSector sector,
        boolean collectionEnabled,
        String permissionStatus,
        String etag,
        String lastModified,
        String extractorConfig,
        String connectorKey
) {
    public CatalogSyncSource(UUID id, UUID providerId, String sourceName, String officialDomain, String sourceUrl,
                             CatalogSourceType sourceType, OfferSector sector, boolean collectionEnabled,
                             String permissionStatus, String etag, String lastModified, String extractorConfig) {
        this(id, providerId, sourceName, officialDomain, sourceUrl, sourceType, sector, collectionEnabled,
                permissionStatus, etag, lastModified, extractorConfig, null);
    }
}
