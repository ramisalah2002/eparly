package com.eparly.api.catalogimport;

public record ProviderImport(
        String key,
        String name,
        String sector,
        String officialWebsite,
        String country
) {
}