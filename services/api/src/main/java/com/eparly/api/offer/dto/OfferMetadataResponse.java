package com.eparly.api.offer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Metadonnees du catalogue d'offres (secteurs, periodicites, definitions)")
public record OfferMetadataResponse(
        @Schema(description = "Secteurs supportes par le catalogue") List<String> sectors,
        @Schema(description = "Periodicites de facturation supportees") List<String> billingPeriods,
        @Schema(description = "Statuts de publication consultables publiquement") List<String> publicStatuses,
        @Schema(description = "Territoires par defaut") List<String> territories,
        @Schema(description = "Devise par defaut", example = "EUR") String defaultCurrency,
        @Schema(description = "Montants en centimes (minor units)") boolean amountsInMinorUnits,
        @Schema(description = "Precision sur la non-exhaustivite du catalogue") String coverageNote
) {
}
