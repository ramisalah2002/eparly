package com.eparly.api.savings.dto;

import com.eparly.api.savings.domain.CostSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Détail du calcul de coûts pour une simulation d'économies")
public record CostBreakdown(

        @Schema(description = "Identifiant de l'abonnement actuel") UUID subscriptionId,
        @Schema(description = "Identifiant de l'offre alternative") UUID offerId,
        @Schema(description = "Horizon en mois") Integer horizonMonths,
        @Schema(description = "Date de début") LocalDate startDate,

        @Schema(description = "Coût actuel total (centimes)") Long currentTotalCostMinor,
        @Schema(description = "Source du coût actuel") CostSource currentCostSource,
        @Schema(description = "Coût alternatif total (centimes)") Long alternativeTotalCostMinor,

        @Schema(description = "Frais de résiliation (centimes)") Long cancellationFeeMinor,
        @Schema(description = "Frais d'activation (centimes)") Long activationFeeMinor,
        @Schema(description = "Autres frais (centimes)") Long otherFeesMinor,
        @Schema(description = "Frais totaux de changement (centimes)") Long totalSwitchingCostMinor,

        @Schema(description = "Économie nette estimée (centimes)") Long estimatedNetSavingsMinor
) {
}
