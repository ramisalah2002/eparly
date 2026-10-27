package com.eparly.api.user.domain;

/**
 * Statut du compte utilisateur au sein de la plateforme Eparly.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    DELETED
}
