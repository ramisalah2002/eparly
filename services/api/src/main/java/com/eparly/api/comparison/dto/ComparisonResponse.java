package com.eparly.api.comparison.dto;

import com.eparly.api.offer.domain.OfferSector;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Reponse globale d'evaluation de comparaison")
public record ComparisonResponse(
        @Schema(description = "Identifiant de l'abonnement analyse") UUID subscriptionId,
        @Schema(description = "Nom de l'abonnement analyse") String subscriptionName,
        @Schema(description = "Fournisseur actuel de l'abonnement") String currentProvider,
        @Schema(description = "Secteur compare") OfferSector sector,
        @Schema(description = "Critères appliqués par l'utilisateur") List<ComparisonCriterionDetail> appliedCriteria,
        @Schema(description = "Offres compatibles (tous critères obligatoires satisfaits)") List<ComparisonOfferResult> compatibleOffers,
        @Schema(description = "Offres incompatibles (au moins un critère obligatoire non satisfait)") List<ComparisonOfferResult> incompatibleOffers,
        @Schema(description = "Offres nécessitant davantage d'informations") List<ComparisonOfferResult> needsInformationOffers,
        @Schema(description = "Offres non éligibles") List<ComparisonOfferResult> notEligibleOffers,
        @Schema(description = "Nombre total d'offres éligibles dans le catalogue pour ce secteur") int totalEligibleOffers,
        @Schema(description = "Fraîcheur des données du catalogue (dernière vérification)") Instant catalogFreshnessAt,
        @Schema(description = "Indique si le catalogue est exhaustif") boolean catalogExhaustive,
        @Schema(description = "Note de transparence commerciale") String commercialTransparencyNote,
        @Schema(description = "Date de l'évaluation") Instant evaluatedAt
) {
}
