package com.eparly.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Répartition des dépenses par catégorie sur une période donnée.
 *
 * Le groupe "Non catégorisé" regroupe les dépenses (EXPENSE + FEE) sans catégorie assignée.
 */
public record SpendingByCategoryResponse(
        @Schema(description = "ID de la catégorie (null pour le groupe Non catégorisé)")
        UUID categoryId,

        @Schema(description = "Nom de la catégorie", example = "Courses")
        String categoryName,

        @Schema(description = "Total des dépenses pour cette catégorie en unités mineures, valeur positive",
                example = "12500")
        Long amountMinor,

        @Schema(description = "Nombre de transactions dans cette catégorie", example = "8")
        Long transactionCount,

        @Schema(description = "Pourcentage des dépenses totales (0-100, arrondi à 2 décimales)",
                example = "27.47")
        Double percentage
) {
}
