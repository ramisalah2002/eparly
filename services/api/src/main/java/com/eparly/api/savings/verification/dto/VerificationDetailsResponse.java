package com.eparly.api.savings.verification.dto;

import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.action.dto.SavingsEstimateSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Détail de la vérification financière d'une action d'économie")
public record VerificationDetailsResponse(
        @Schema(description = "Identifiant de l'action concernée")
        UUID actionId,

        @Schema(description = "Statut de vérification financière")
        VerificationStatus verificationStatus,

        @Schema(description = "Date effective du changement déclarée par l'utilisateur")
        LocalDate effectiveChangeDate,

        @Schema(description = "Date de déclaration du changement")
        Instant declaredAt,

        @Schema(description = "Paiements observés de l'ancien abonnement")
        List<ObservedPaymentSummary> observedOldPayments,

        @Schema(description = "Paiements observés du nouvel abonnement")
        List<ObservedPaymentSummary> observedNewPayments,

        @Schema(description = "Montant mensuel moyen observé de l'ancien abonnement en centimes")
        Long observedOldMonthlyAmountMinor,

        @Schema(description = "Montant mensuel moyen observé du nouvel abonnement en centimes")
        Long observedNewMonthlyAmountMinor,

        @Schema(description = "Écart mensuel brut constaté entre anciens et nouveaux paiements observés")
        Long observedMonthlyDifferenceMinor,

        @Schema(description = "Date de début de la période observée")
        LocalDate coveredFrom,

        @Schema(description = "Date de fin de la période observée")
        LocalDate coveredTo,

        @Schema(description = "Informations manquantes empêchant une vérification supérieure")
        List<String> missingInformation,

        @Schema(description = "Contradictions ou anomalies détectées")
        List<String> contradictions,

        @Schema(description = "Raisons explicatives de la décision de statut")
        List<String> decisionReasons,

        @Schema(description = "Instantané de l'estimation initiale")
        SavingsEstimateSnapshot initialEstimateSnapshot,

        @Schema(description = "Comparaison entre l'estimation initiale et les montants réellement observés")
        String differenceVersusEstimate
) {
}
