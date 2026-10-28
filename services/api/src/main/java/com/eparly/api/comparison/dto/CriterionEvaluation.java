package com.eparly.api.comparison.dto;

import com.eparly.api.comparison.domain.CriterionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultat d'evaluation d'un critere de comparaison")
public record CriterionEvaluation(
        @Schema(description = "Code du critere (ex: minScreens, minDataGb)") String code,
        @Schema(description = "Nom lisible du critere") String label,
        @Schema(description = "Valeur exigee par l'utilisateur") Object requiredValue,
        @Schema(description = "Valeur offerte par l'offre commerciale") Object offeredValue,
        @Schema(description = "Resultat: SATISFIED, NOT_SATISFIED, UNKNOWN, NOT_ELIGIBLE") CriterionResult result,
        @Schema(description = "Explication compréhensible du résultat") String explanation,
        @Schema(description = "Indique si le critère est obligatoire") boolean mandatory,
        @Schema(description = "Source verifiee de l'information offerte") String source
) {
}
