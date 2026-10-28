package com.eparly.api.savings.domain;

/**
 * Origine du montant de coût utilisé pour le calcul d'économies.
 * Une observation bancaire n'est pas un tarif contractuel garanti.
 */
public enum CostSource {
    USER_DECLARED,   // Montant déclaré par l'utilisateur
    TRANSACTION_OBSERVED, // Montant observé dans les transactions bancaires
    UNKNOWN          // Aucune donnée disponible
}
