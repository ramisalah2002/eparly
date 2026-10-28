package com.eparly.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Évolution mensuelle des dépenses et revenus sur une période glissante.
 *
 * Les mois sans transactions sont inclus avec des indicateurs nuls,
 * sans prétendre que cela prouve l'absence d'opérations bancaires.
 */
public record MonthlyTrendResponse(
        @Schema(description = "Mois au format YYYY-MM", example = "2025-03")
        String month,

        @Schema(description = "Total des dépenses du mois (EXPENSE + FEE) en unités mineures, valeur positive",
                example = "35000")
        Long expensesMinor,

        @Schema(description = "Revenus confirmés du mois (INCOME positif) en unités mineures, valeur positive",
                example = "125000")
        Long confirmedIncomeMinor,

        @Schema(description = "Montants positifs non classés du mois (UNKNOWN positif, REFUND) en unités mineures",
                example = "35020")
        Long unclassifiedPositiveMinor,

        @Schema(description = "Nombre de transactions dans le mois", example = "15")
        Long transactionCount
) {
}
