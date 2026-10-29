package com.eparly.api.savings.action.domain;

/**
 * Niveau de vérification financière d'une action d'économie.
 * Pour l'étape 11A, aucune économie ne doit être automatiquement VERIFIED_OBSERVED.
 * La vérification financière réelle sera traitée à l'étape 11B.
 */
public enum VerificationStatus {
    NOT_VERIFIED,      // Aucune vérification effectuée (état par défaut)
    PARTIALLY_VERIFIED, // Preuves partielles (ex: confirmation de souscription reçue)
    VERIFIED_OBSERVED   // Économie étayée par des transactions bancaires postérieures
}
