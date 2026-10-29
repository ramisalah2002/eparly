package com.eparly.api.savings.action.dto;

import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.savings.domain.SavingsStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Instantané figé de l'estimation d'économies au moment de la création d'une action.
 * Conservé tel quel même si le catalogue ou les prix évoluent ultérieurement.
 */
@Schema(description = "Instantané de l'estimation d'économies au moment de la décision")
public record SavingsEstimateSnapshot(

        @Schema(description = "Version du calcul d'estimation") String calculationVersion,

        @Schema(description = "Horizon de comparaison en mois") Integer horizonMonths,

        @Schema(description = "Devise utilisée") String currency,

        @Schema(description = "Coût actuel total sur l'horizon (centimes)") Long currentTotalCostMinor,

        @Schema(description = "Source du coût actuel") CostSource currentCostSource,

        @Schema(description = "Coût alternatif total sur l'horizon (centimes)") Long alternativeTotalCostMinor,

        @Schema(description = "Frais totaux de changement (centimes)") Long switchingCostMinor,

        @Schema(description = "Économie nette estimée (centimes)") Long potentialNetSavingsMinor,

        @Schema(description = "Statut de l'estimation") SavingsStatus estimationStatus,

        @Schema(description = "Date de vérification de l'offre source") Instant offerVerifiedAt,

        @Schema(description = "Date de l'estimation") Instant estimatedAt,

        @Schema(description = "Hypothèses utilisées") List<String> assumptions,

        @Schema(description = "Informations manquantes") List<String> missingInformation,

        @Schema(description = "URL officielle de la source vérifiée") String officialUrl
) {
}
