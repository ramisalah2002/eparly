package com.eparly.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Résumé statistique des transactions sur une période donnée.
 *
 * IMPORTANT : ces chiffres proviennent d'imports CSV potentiellement incomplets.
 * Ils ne représentent pas un solde bancaire réel ni une épargne garantie.
 */
public record DashboardSummaryResponse(
        @Schema(description = "Total des dépenses identifiées (EXPENSE + FEE) en unités mineures, valeur positive",
                example = "45500")
        Long totalExpensesMinor,

        @Schema(description = "Revenus confirmés (INCOME avec montant positif) en unités mineures, valeur positive",
                example = "125000")
        Long confirmedIncomeMinor,

        @Schema(description = "Dépenses non catégorisées (EXPENSE sans catégorie) en unités mineures, valeur positive",
                example = "2500")
        Long unclassifiedExpensesMinor,

        @Schema(description = "Montants positifs non classés (UNKNOWN positif, REFUND, etc.) en unités mineures",
                example = "35020")
        Long unclassifiedPositiveMinor,

        @Schema(description = "Nombre total de transactions dans la période", example = "42")
        Long transactionsCount,

        @Schema(description = "Nombre de transactions sans catégorie dans la période", example = "5")
        Long uncategorizedTransactionsCount,

        @Schema(description = "Date de la première transaction dans la période", example = "2025-01-15")
        LocalDate firstTransactionDate,

        @Schema(description = "Date de la dernière transaction dans la période", example = "2025-03-10")
        LocalDate lastTransactionDate,

        @Schema(description = "Devise des agrégats (uniquement EUR accepté pour le MVP)", example = "EUR")
        String currency,

        @Schema(description = "Indique que les données proviennent d'imports CSV potentiellement incomplets. "
                + "Le dashboard ne doit pas être interprété comme un solde bancaire réel.",
                example = "true")
        boolean basedOnIncompleteData
) {
}
