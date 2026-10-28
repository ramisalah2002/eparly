package com.eparly.api.savings.domain;

/**
 * Statut du résultat d'estimation d'économies.
 * Permet de distinguer une économie réelle estimée d'un surcoût,
 * d'une absence de données ou d'une offre non éligible.
 */
public enum SavingsStatus {
    SAVINGS_POSSIBLE,   // Gain net estimé positif, données suffisantes
    NO_SAVINGS,         // Coût équivalent ou supérieur, données suffisantes
    INSUFFICIENT_DATA,  // Coût, frais ou conditions nécessaires manquants
    INCOMPATIBLE_OFFER, // Offre non compatible (comparaison 10B)
    NOT_ELIGIBLE,       // Conditions commerciales non remplies
    UNVERIFIED_OFFER    // Prix ou offre non vérifiable
}
