package com.eparly.api.catalogsync.normalization;

import java.util.List;

/**
 * Indique si une offre observee dispose des caracteristiques permettant une comparaison.
 * Il s'agit d'un prerequis documente : le batch ne calcule jamais d'economie.
 */
public record CatalogComparability(Status status, String comparisonGroup, List<String> missingAttributes) {
    public enum Status {
        COMPARABLE_WITHIN_GROUP,
        INSUFFICIENT_ATTRIBUTES,
        NOT_COMPARABLE
    }
}
