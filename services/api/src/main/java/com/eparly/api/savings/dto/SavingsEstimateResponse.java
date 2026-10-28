package com.eparly.api.savings.dto;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.savings.domain.SavingsStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Résultat d'estimation d'économies nettes potentielles")
public record SavingsEstimateResponse(

        @Schema(description = "Identifiant de l'abonnement actuel") UUID subscriptionId,
        @Schema(description = "Nom de l'abonnement actuel") String subscriptionName,
        @Schema(description = "Fournisseur actuel") String currentProvider,
        @Schema(description = "Identifiant de l'offre alternative") UUID offerId,
        @Schema(description = "Nom commercial de l'offre alternative") String alternativeOfferName,
        @Schema(description = "Fournisseur de l'offre alternative") String alternativeProvider,
        @Schema(description = "Secteur comparé") OfferSector sector,

        @Schema(description = "Statut financier: SAVINGS_POSSIBLE, NO_SAVINGS, INSUFFICIENT_DATA, INCOMPATIBLE_OFFER, NOT_ELIGIBLE, UNVERIFIED_OFFER")
        SavingsStatus status,

        @Schema(description = "Horizon de comparaison en mois") Integer horizonMonths,
        @Schema(description = "Date de début de la simulation") LocalDate simulationStartDate,

        // Coût actuel
        @Schema(description = "Coût actuel total sur l'horizon (centimes)") Long currentTotalCostMinor,
        @Schema(description = "Source du coût actuel") CostSource currentCostSource,
        @Schema(description = "Coût actuel mensuel (centimes)") Long currentMonthlyCostMinor,
        @Schema(description = "Coût actuel annuel (centimes)") Long currentAnnualCostMinor,

        // Coût alternatif
        @Schema(description = "Coût alternatif total sur l'horizon (centimes)") Long alternativeTotalCostMinor,
        @Schema(description = "Coût alternatif mensuel (centimes)") Long alternativeMonthlyCostMinor,
        @Schema(description = "Coût alternatif annuel (centimes)") Long alternativeAnnualCostMinor,

        // Promotion
        @Schema(description = "Prix promotionnel utilisé (centimes)") Long promotionPriceMinor,
        @Schema(description = "Durée de la promotion en mois") Integer promotionDurationMonths,
        @Schema(description = "Prix après promotion (centimes)") Long priceAfterPromotionMinor,

        // Frais de changement
        @Schema(description = "Frais de résiliation (centimes)") Long cancellationFeeMinor,
        @Schema(description = "Frais d'activation (centimes)") Long activationFeeMinor,
        @Schema(description = "Autres frais (centimes)") Long otherFeesMinor,
        @Schema(description = "Frais totaux de changement (centimes)") Long totalSwitchingCostMinor,

        // Résultat
        @Schema(description = "Économie nette estimée (centimes), négatif = surcoût") Long estimatedNetSavingsMinor,
        @Schema(description = "Économie mensuelle estimée (centimes)") Long estimatedMonthlySavingsMinor,

        // Explications
        @Schema(description = "Explication accessible pour l'utilisateur") String explanation,
        @Schema(description = "Hypothèses utilisées") List<String> assumptions,
        @Schema(description = "Informations manquantes") List<String> missingInformation,
        @Schema(description = "Détail du calcul par étape") List<String> calculationSteps,

        // Traçabilité
        @Schema(description = "URL officielle de la source vérifiée") String officialUrl,
        @Schema(description = "Date de dernière vérification de l'offre") Instant offerVerifiedAt,
        @Schema(description = "Date de l'estimation") Instant estimatedAt
) {
}
