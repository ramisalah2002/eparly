package com.eparly.api.savings.action.dto;

import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Réponse détaillant une action d'économie")
public record SavingsActionResponse(

        @Schema(description = "Identifiant de l'action") UUID id,
        @Schema(description = "Identifiant de l'abonnement concerné") UUID subscriptionId,
        @Schema(description = "Nom de l'abonnement concerné") String subscriptionName,
        @Schema(description = "Fournisseur actuel") String currentProvider,
        @Schema(description = "Identifiant de l'offre alternative") UUID alternativeOfferId,
        @Schema(description = "Nom commercial de l'offre alternative") String alternativeOfferName,
        @Schema(description = "Fournisseur de l'offre alternative") String alternativeProvider,
        @Schema(description = "Secteur comparé") String sector,

        @Schema(description = "Statut de l'action: PLANNED, COMMITTED, DECLARED, CANCELED") ActionStatus status,
        @Schema(description = "Statut de vérification financière") VerificationStatus verificationStatus,

        @Schema(description = "Horizon de comparaison en mois") Integer horizonMonths,

        @Schema(description = "Date de création") Instant createdAt,
        @Schema(description = "Date de dernière mise à jour") Instant updatedAt,
        @Schema(description = "Date d'engagement des démarches") Instant committedAt,
        @Schema(description = "Date de déclaration de changement") Instant declaredAt,
        @Schema(description = "Date d'annulation") Instant canceledAt,

        @Schema(description = "Instantané de l'estimation au moment de la création") SavingsEstimateSnapshot estimateSnapshot,

        @Schema(description = "Indication que l'estimation peut être obsolète") boolean estimateMayBeStale,

        @Schema(description = "Notes") String notes
) {
}
