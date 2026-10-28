package com.eparly.api.comparison.dto;

import com.eparly.api.offer.domain.OfferSector;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Definition d'un critere de comparaison pour un secteur")
public record ComparisonCriterionDetail(
        @Schema(description = "Code du critere") String code,
        @Schema(description = "Libelle lisible") String label,
        @Schema(description = "Secteur concerne") OfferSector sector,
        @Schema(description = "Type de champ (string, number, boolean, list)") String fieldType,
        @Schema(description = "Valeurs autorises (si enum)") List<String> allowedValues,
        @Schema(description = "Indique si le critere est obligatoire") boolean mandatory,
        @Schema(description = "Description du critere") String description,
        @Schema(description = "Valeur par défaut si aucune (null si aucune)") Object defaultValue
) {
}
