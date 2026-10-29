package com.eparly.api.catalogsync.fetch;

import java.net.URI;

public record FetchResult(int statusCode, URI finalUri, String contentType, String body,
                          String etag, String lastModified) {
    public boolean notModified() {
        return statusCode == 304;
    }
}
