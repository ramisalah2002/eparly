package com.eparly.api.comparison.dto;

import com.eparly.api.offer.domain.OfferSector;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Metadonnees du moteur de comparaison")
public record ComparisonMetadataResponse(
        @Schema(description = "Secteurs supportes") List<OfferSector> supportedSectors,
        @Schema(description = "Version du moteur de comparaison") String engineVersion,
        @Schema(description = "Methode generale de comparaison") String comparisonMethod,
        @Schema(description = "Conditions de referencement des offres") String referencingConditions,
        @Schema(description = "Critères de tri appliques") String sortingCriteria,
        @Schema(description = "Politique de fraicheur des donnees") String freshnessPolicy,
        @Schema(description = "Note sur la couverture limitee du catalogue") String coverageNote,
        @Schema(description = "Information sur la transparence commerciale") String commercialTransparencyNote
) {
}
