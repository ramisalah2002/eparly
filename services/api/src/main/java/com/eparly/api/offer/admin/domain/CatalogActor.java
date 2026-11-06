package com.eparly.api.offer.admin.domain;

import java.util.UUID;

/**
 * Auteur reel d'une operation du catalogue. Un import est une operation systeme : la personne qui l'a demande
 * figure dans requestedByUserId, jamais comme acteur, et un import ne peut pas produire de verification humaine.
 */
public record CatalogActor(UUID userId, CatalogExecutionMode mode, UUID requestedByUserId) {

    public static CatalogActor admin(UUID userId) {
        return new CatalogActor(userId, CatalogExecutionMode.ADMIN_ACTION, null);
    }

    public static CatalogActor manualVerification(UUID userId) {
        return new CatalogActor(userId, CatalogExecutionMode.MANUAL_VERIFICATION, null);
    }

    public static CatalogActor systemImport(UUID requestedByUserId) {
        return new CatalogActor(null, CatalogExecutionMode.SYSTEM_IMPORT, requestedByUserId);
    }
}
