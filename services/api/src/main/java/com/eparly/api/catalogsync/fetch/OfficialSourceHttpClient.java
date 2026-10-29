package com.eparly.api.catalogsync.fetch;

import com.eparly.api.catalogsync.domain.CatalogSyncSource;

public interface OfficialSourceHttpClient {
    FetchResult fetch(CatalogSyncSource source, String url, String etag, String lastModified);
}
