package com.eparly.api.catalogimport;

import java.util.ArrayList;
import java.util.List;

public record CatalogImportReport(
        String file,
        boolean apply,
        int providersPlanned,
        int providersCreated,
        int providersExisting,
        int offersPlanned,
        int offersCreated,
        int offersExisting,
        int offersSkipped,
        List<String> errors,
        List<String> actions
) {
    public static CatalogImportReport empty(String file, boolean apply) {
        return new CatalogImportReport(file, apply, 0, 0, 0, 0, 0, 0, 0, new ArrayList<>(), new ArrayList<>());
    }
}