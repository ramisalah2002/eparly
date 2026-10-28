package com.eparly.api.savings.service;

import com.eparly.api.savings.domain.SavingsStatus;
import com.eparly.api.savings.dto.SavingsEstimateResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class SavingsExplanationBuilder {

    /**
     * Construit l'explication globale accessible pour l'utilisateur.
     */
    public String buildGlobalExplanation(SavingsStatus status, Long netSavingsMinor, String currency) {
        return switch (status) {
            case SAVINGS_POSSIBLE -> {
                if (netSavingsMinor != null && netSavingsMinor > 0) {
                    BigDecimal euros = BigDecimal.valueOf(netSavingsMinor).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    yield "Économie nette estimée de " + euros + " " + currency + " sur l'horizon de comparaison.";
                }
                yield "Économie nette estimée positive sur l'horizon de comparaison.";
            }
            case NO_SAVINGS -> "L'offre alternative n'apporte pas d'économie nette sur l'horizon de comparaison (coût équivalent ou supérieur).";
            case INSUFFICIENT_DATA -> "Données insuffisantes pour estimer les économies de manière fiable.";
            case INCOMPATIBLE_OFFER -> "L'offre alternative n'est pas compatible avec les critères de l'utilisateur.";
            case NOT_ELIGIBLE -> "L'utilisateur ne remplit pas les conditions d'éligibilité de cette offre.";
            case UNVERIFIED_OFFER -> "L'offre alternative n'est pas vérifiée ou ses données ne sont pas fiables.";
        };
    }

    /**
     * Construit la liste des hypothèses utilisées dans le calcul.
     */
    public List<String> buildAssumptions(
            Integer horizonMonths,
            Boolean promotionApplied,
            Long switchingCostMinor,
            List<String> missingFees,
            boolean currentCostIsObserved
    ) {
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Horizon de comparaison : " + horizonMonths + " mois.");
        assumptions.add("Périodicité de facturation conservée entre l'offre actuelle et l'alternative.");
        if (promotionApplied != null && promotionApplied) {
            assumptions.add("Tarif promotionnel appliqué pendant la durée de la promotion, puis prix normal.");
        } else {
            assumptions.add("Aucune promotion appliquée (prix normal sur toute la période).");
        }
        if (switchingCostMinor != null && switchingCostMinor > 0) {
            assumptions.add("Frais de changement inclus : " + switchingCostMinor / 100.0 + " €.");
        }
        if (missingFees != null && !missingFees.isEmpty()) {
            assumptions.add("Frais inconnus non inclus : " + String.join(", ", missingFees) + ".");
        }
        if (currentCostIsObserved) {
            assumptions.add("Coût actuel basé sur l'observation bancaire (pas un tarif contractuel garanti).");
        } else {
            assumptions.add("Coût actuel basé sur la déclaration utilisateur.");
        }
        assumptions.add("Les montants sont en " + (currentCostIsObserved ? "centimes observés" : "centimes déclarés") + ".");
        assumptions.add("Aucun taux de change appliqué (comparaison limitée à l'EUR).");
        return assumptions;
    }

    /**
     * Construit la liste des informations manquantes.
     */
    public List<String> buildMissingInformation(
            boolean currentCostMissing,
            List<String> missingFees,
            boolean promotionUncertain
    ) {
        List<String> missing = new ArrayList<>();
        if (currentCostMissing) {
            missing.add("Coût actuel de l'abonnement (ni déclaré, ni observé).");
        }
        if (missingFees != null) {
            for (String fee : missingFees) {
                missing.add("Frais inconnu : " + fee + ".");
            }
        }
        if (promotionUncertain) {
            missing.add("Conditions de promotion non vérifiées pour cet utilisateur.");
        }
        return missing;
    }

    /**
     * Construit les étapes de calcul pour la transparence.
     */
    public List<String> buildCalculationSteps(
            Long currentTotalCostMinor,
            Long alternativeTotalCostMinor,
            Long switchingCostMinor,
            Long netSavingsMinor
    ) {
        List<String> steps = new ArrayList<>();
        steps.add("1. Coût actuel total sur l'horizon : " + formatAmount(currentTotalCostMinor) + " €.");
        steps.add("2. Coût alternatif total sur l'horizon : " + formatAmount(alternativeTotalCostMinor) + " €.");
        steps.add("3. Frais de changement (résiliation + activation + autres) : " + formatAmount(switchingCostMinor) + " €.");
        steps.add("4. Économie nette estimée = Coût actuel − Coût alternatif − Frais de changement = "
                + formatAmount(netSavingsMinor) + " €.");
        if (netSavingsMinor != null && netSavingsMinor < 0) {
            steps.add("Résultat négatif : l'alternative représente un surcoût estimé de "
                    + formatAmount(-netSavingsMinor) + " €.");
        }
        return steps;
    }

    private String formatAmount(Long minor) {
        if (minor == null) return "non renseigné";
        BigDecimal euros = BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return euros.toString();
    }
}
