package com.eparly.api.comparison.dto;

import com.eparly.api.comparison.domain.ComparisonStatus;
import com.eparly.api.offer.domain.PromotionPriceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resultat d'evaluation d'une offre commerciale pour un utilisateur donne")
public record ComparisonOfferResult(
        @Schema(description = "Identifiant de l'offre commerciale") UUID offerId,
        @Schema(description = "Nom commercial de l'offre") String commercialName,
        @Schema(description = "Nom du fournisseur") String providerName,
        @Schema(description = "Secteur de l'offre") String sector,
        @Schema(description = "Statut global: COMPATIBLE, INCOMPATIBLE, NEEDS_INFORMATION, NOT_ELIGIBLE") ComparisonStatus status,
        @Schema(description = "Detail de chaque critere evalue") List<CriterionEvaluation> criteria,
        @Schema(description = "Prix normal en centimes") Long amountMinor,
        @Schema(description = "Devise") String currency,
        @Schema(description = "Periodicite de facturation") String billingPeriod,
        @Schema(description = "Prix promotionnel fixe en unités mineures par cycle (FIXED_PRICE uniquement)") Long priceAfterPromotionMinor,
        @Schema(description = "Pourcentage de promotion") Integer promotionPercent,
        @Schema(description = "Durée de la promotion en mois") Integer promotionDurationMonths,
        @Schema(description = "Frais d'activation en centimes") Long activationFeeMinor,
        @Schema(description = "Engagement (texte libre)") String commitment,
        @Schema(description = "Territoire") String territory,
        @Schema(description = "URL officielle de la source verifiee") String officialUrl,
        @Schema(description = "Date de derniere verification") Instant verifiedAt,
        @Schema(description = "Score heuristique de tri (0-100), pas une probabilite") Integer heuristicScore,
        @Schema(description = "Méthode de calcul de la promotion actuellement vérifiée") PromotionPriceType promotionPriceType,
        @Schema(description = "Début de la fenêtre de souscription à la promotion") LocalDate promotionValidFrom,
        @Schema(description = "Fin de la fenêtre de souscription à la promotion") LocalDate promotionValidUntil,
        @Schema(description = "Indique que la promotion est vérifiée, fraîche et applicable aujourd'hui") boolean promotionVerified
) {
}
