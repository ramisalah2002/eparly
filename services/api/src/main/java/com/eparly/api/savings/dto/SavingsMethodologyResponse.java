package com.eparly.api.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Documentation de la méthodologie de calcul des économies")
public record SavingsMethodologyResponse(

        @Schema(description = "Formule de calcul") String formula,
        @Schema(description = "Horizons supportés en mois") List<Integer> supportedHorizonsMonths,
        @Schema(description = "Périodicités supportées") List<String> supportedBillingPeriods,
        @Schema(description = "Devise supportée") String supportedCurrency,
        @Schema(description = "Arrondi utilisé") String roundingMode,
        @Schema(description = "Traitement des promotions") String promotionHandling,
        @Schema(description = "Traitement des frais inconnus") String unknownFeesHandling,
        @Schema(description = "Sources de prix") String priceSources,
        @Schema(description = "Fraîcheur des données") String freshnessPolicy,
        @Schema(description = "Limites de l'estimation") String estimationLimits,
        @Schema(description = "Transparence commerciale") String commercialTransparencyNote
) {
}
