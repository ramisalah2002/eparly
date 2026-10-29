package com.eparly.api.savings.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Résumé du plan d'économies de l'utilisateur")
public record SavingsPlanSummaryResponse(

        @Schema(description = "Nombre total d'actions") int actionsCount,
        @Schema(description = "Nombre d'actions PLANNED") int plannedCount,
        @Schema(description = "Nombre d'actions COMMITTED") int committedCount,
        @Schema(description = "Nombre d'actions DECLARED") int declaredCount,
        @Schema(description = "Nombre d'actions CANCELED") int canceledCount,

        @Schema(description = "Économie potentielle totale des actions PLANNED/COMMITTED/DECLARED (centimes)") Long potentialSavingsMinor,
        @Schema(description = "Économie potentielle des actions COMMITTED (centimes)") Long committedPotentialSavingsMinor,
        @Schema(description = "Économie potentielle des actions DECLARED (centimes)") Long declaredPotentialSavingsMinor,

        @Schema(description = "Économie observée (centimes) - toujours nul à l'étape 11A") Long observedSavingsMinor,
        @Schema(description = "Statut de vérification financière") String verificationStatus,

        @Schema(description = "Devise utilisée") String currency,
        @Schema(description = "Horizon de comparaison en mois") Integer horizonMonths,

        @Schema(description = "Note sur le caractère non exhaustif des économies potentielles") String note
) {
}
