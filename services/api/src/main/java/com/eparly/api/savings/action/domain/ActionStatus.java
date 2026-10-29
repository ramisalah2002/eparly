package com.eparly.api.savings.action.domain;

/**
 * Statut d'une action d'économie dans le plan personnel de l'utilisateur.
 * Ce statut ne représente PAS la vérification financière des économies.
 */
public enum ActionStatus {
    PLANNED,    // Opportunité ajoutée au plan, aucune démarche engagée
    COMMITTED,  // L'utilisateur déclare avoir engagé les démarches de changement
    DECLARED,   // L'utilisateur déclare avoir effectué le changement d'offre
    CANCELED    // L'utilisateur abandonne ou retire cette action
}
