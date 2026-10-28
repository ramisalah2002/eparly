package com.eparly.api.savings.dto;

import com.eparly.api.offer.domain.OfferSector;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Requête d'estimation d'économies pour le remplacement d'un abonnement.
 */
@Schema(description = "Requête d'estimation d'économies nettes potentielles")
public record SavingsEstimateRequest(

        @NotNull(message = "subscriptionId is required")
        @Schema(description = "Identifiant de l'abonnement actuel", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID subscriptionId,

        @NotNull(message = "offerId is required")
        @Schema(description = "Identifiant de l'offre alternative", example = "660e8400-e29b-41d4-a716-446655440000")
        UUID offerId,

        @NotNull(message = "sector is required")
        @Schema(description = "Secteur du service", example = "MOBILE_PLAN")
        OfferSector sector,

        @Min(1)
        @Max(60)
        @Schema(description = "Horizon de comparaison en mois (défaut 12)", example = "12")
        Integer horizonMonths,

        @Size(max = 1000)
        @Schema(description = "Critères de comparaison (reutilisés de l'étape 10B)")
        Map<String, Object> requirements,

        @Schema(description = "Frais de résiliation connus de l'utilisateur en centimes (optionnel)")
        @DecimalMin("0")
        Long userKnownCancellationFeeMinor,

        @Schema(description = "Autres frais de changement connus de l'utilisateur en centimes (optionnel)")
        @DecimalMin("0")
        Long userKnownOtherFeesMinor
) {
}
