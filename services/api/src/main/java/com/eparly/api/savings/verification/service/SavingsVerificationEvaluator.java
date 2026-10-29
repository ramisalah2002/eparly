package com.eparly.api.savings.verification.service;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.verification.domain.EvidenceRole;
import com.eparly.api.savings.verification.domain.SavingsActionEvidence;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.transaction.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class SavingsVerificationEvaluator {

    private final OfferPricingRepository offerPricingRepository;

    public SavingsVerificationEvaluator(OfferPricingRepository offerPricingRepository) {
        this.offerPricingRepository = offerPricingRepository;
    }

    public record EvaluationResult(
            VerificationStatus status,
            Long oldMonthlyAmountMinor,
            Long newMonthlyAmountMinor,
            Long observedDifferenceMinor,
            LocalDate coveredFrom,
            LocalDate coveredTo,
            List<String> missingInformation,
            List<String> contradictions,
            List<String> decisionReasons,
            String differenceVersusEstimate
    ) {
    }

    public EvaluationResult evaluate(
            SavingsAction action,
            List<SavingsActionEvidence> evidences,
            List<Transaction> userRecentTransactions
    ) {
        List<String> missingInformation = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        List<String> decisionReasons = new ArrayList<>();

        List<SavingsActionEvidence> oldEvidences = evidences.stream()
                .filter(e -> e.getRole() == EvidenceRole.OLD_SUBSCRIPTION_PAYMENT)
                .toList();

        List<SavingsActionEvidence> newEvidences = evidences.stream()
                .filter(e -> e.getRole() == EvidenceRole.NEW_SUBSCRIPTION_PAYMENT)
                .toList();

        // 1. Comparaison des équivalents mensuels uniquement si la périodicité est connue.
        RecurringFrequency oldFrequency = action.getSubscription().getDeclaredFrequency();
        Optional<OfferPricing> newPricing = offerPricingRepository.findByOfferId(action.getAlternativeOffer().getId());
        OfferBillingPeriod newBillingPeriod = newPricing.map(OfferPricing::getBillingPeriod).orElse(null);
        Long oldMonthlyAmountMinor = computeAverageMonthlyAmount(oldEvidences, monthlyFactor(oldFrequency));
        Long newMonthlyAmountMinor = computeAverageMonthlyAmount(newEvidences, monthlyFactor(newBillingPeriod));
        Long observedDifferenceMinor = null;
        if (oldMonthlyAmountMinor != null && newMonthlyAmountMinor != null) {
            observedDifferenceMinor = oldMonthlyAmountMinor - newMonthlyAmountMinor;
        }

        // 2. Détermination de la couverture temporelle
        LocalDate coveredFrom = null;
        LocalDate coveredTo = null;
        for (SavingsActionEvidence e : evidences) {
            LocalDate d = e.getTransaction().getBookingDate();
            if (coveredFrom == null || d.isBefore(coveredFrom)) coveredFrom = d;
            if (coveredTo == null || d.isAfter(coveredTo)) coveredTo = d;
        }

        // 3. Détection des informations manquantes
        boolean isDeclared = action.getStatus() == ActionStatus.DECLARED;
        if (!isDeclared) {
            missingInformation.add("L'action est au statut " + action.getStatus()
                    + " : le changement d'offre n'a pas encore été déclaré comme effectif par l'utilisateur.");
        }

        LocalDate effectiveDate = action.getEffectiveChangeDate();
        if (effectiveDate == null) {
            missingInformation.add("La date effective du changement n'a pas été explicitement renseignée par l'utilisateur.");
        }

        if (newEvidences.isEmpty()) {
            missingInformation.add("Aucune transaction bancaire vers le nouveau fournisseur n'a été rattachée comme preuve.");
        } else if (newEvidences.size() == 1) {
            missingInformation.add("Une seule échéance du nouvel abonnement a été observée : confirmation de régularité dans la durée impossible.");
        }

        if (oldEvidences.isEmpty()) {
            missingInformation.add("Aucun paiement historique de l'ancien abonnement n'a été rattaché pour comparaison empirique directe.");
        } else if (oldMonthlyAmountMinor == null) {
            missingInformation.add("La périodicité déclarée de l'ancien abonnement est inconnue : son montant ne peut pas être normalisé en équivalent mensuel.");
        }

        if (!newEvidences.isEmpty() && newMonthlyAmountMinor == null) {
            missingInformation.add("La périodicité de facturation de la nouvelle offre est inconnue : son montant ne peut pas être normalisé en équivalent mensuel.");
        }

        // Vérification de la complétude du CSV
        LocalDate referenceDate = effectiveDate;
        if (referenceDate != null && userRecentTransactions != null && !userRecentTransactions.isEmpty()) {
            LocalDate latestTxDate = userRecentTransactions.getFirst().getBookingDate();
            if (latestTxDate.isBefore(referenceDate.plusDays(30))) {
                missingInformation.add("Les transactions disponibles ne s'étendent pas sur 30 jours après la date effective ; la complétude CSV ne peut pas être confirmée.");
            }
        } else if (referenceDate != null) {
            missingInformation.add("Aucune transaction disponible pour évaluer la couverture après la date effective.");
        } else {
            missingInformation.add("La couverture des transactions autour du changement ne peut pas être évaluée sans date effective explicite.");
        }

        // 4. Détection des contradictions
        if (referenceDate != null && userRecentTransactions != null) {
            Subscription sub = action.getSubscription();
            String oldProvider = cleanString(sub.getProvider());
            String oldName = cleanString(sub.getName());

            for (Transaction tx : userRecentTransactions) {
                if (!tx.isExpense()) continue;
                if (tx.getBookingDate().isAfter(referenceDate)) {
                    String label = cleanString(tx.getNormalizedLabel());
                    if (containsKeyword(label, oldProvider) || containsKeyword(label, oldName)) {
                        // Un débit de l'ancien fournisseur continue après la date de changement !
                        contradictions.add(String.format(Locale.FRANCE,
                                "Débit de l'ancien fournisseur observé après la date effective (%s : %s, %.2f €).",
                                tx.getBookingDate(), tx.getNormalizedLabel(), Math.abs(tx.getAmountMinor()) / 100.0));
                        break;
                    }
                }
            }
        }

        // Détection de simultanéité dans les preuves observées
        for (SavingsActionEvidence oldEv : oldEvidences) {
            for (SavingsActionEvidence newEv : newEvidences) {
                long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                        oldEv.getTransaction().getBookingDate(),
                        newEv.getTransaction().getBookingDate()
                ));
                if (daysDiff <= 15) {
                    contradictions.add(String.format(Locale.FRANCE,
                            "Paiements de l'ancien et du nouveau fournisseur observés à seulement %d jours d'intervalle (%s et %s).",
                            daysDiff, oldEv.getTransaction().getBookingDate(), newEv.getTransaction().getBookingDate()));
                    break;
                }
            }
            if (!contradictions.isEmpty()) break;
        }

        // 5. Décision de statut
        VerificationStatus status;
        if (!isDeclared || newEvidences.isEmpty()) {
            status = VerificationStatus.NOT_VERIFIED;
            decisionReasons.add("Aucune preuve bancaire suffisante ne permet d'étayer le changement.");
            if (!isDeclared) {
                decisionReasons.add("L'action doit être au statut DECLARED pour pouvoir être vérifiée.");
            }
            if (newEvidences.isEmpty()) {
                decisionReasons.add("Au moins une transaction observée vers le nouveau fournisseur est nécessaire.");
            }
        } else {
            // Dans notre modèle MVP avec imports CSV, un changement déclaré avec au moins une transaction observée
            // atteint PARTIALLY_VERIFIED.
            status = VerificationStatus.PARTIALLY_VERIFIED;
            decisionReasons.add(String.format(Locale.FRANCE,
                    "Paiements vers le nouveau fournisseur observés (%d transaction(s) rattachée(s)).",
                    newEvidences.size()));

            if (oldMonthlyAmountMinor != null && newMonthlyAmountMinor != null) {
                decisionReasons.add(String.format(Locale.FRANCE,
                        "Écart mensuel brut observé de %.2f € (ancien : %.2f €, nouveau : %.2f €).",
                        (observedDifferenceMinor != null ? observedDifferenceMinor : 0L) / 100.0,
                        oldMonthlyAmountMinor / 100.0,
                        newMonthlyAmountMinor / 100.0));
            }

            decisionReasons.add("L'écart brut entre paiements ne constitue pas une économie nette constatée : les frais de changement, les doubles prélèvements et la complétude de la période ne sont pas établis par ces seuls paiements.");

            // Documenter explicitement pourquoi VERIFIED_OBSERVED n'est pas attribué
            decisionReasons.add("Le statut VERIFIED_OBSERVED reste réservé et non attribué dans cette version MVP : "
                    + "les imports CSV ne prouvent pas la résiliation juridique de l'ancien abonnement, "
                    + "et l'absence de transaction ne constitue pas une preuve de fin de facturation.");
        }

        // 6. Comparaison avec l'estimation initiale
        String differenceVersusEstimate = buildDifferenceVersusEstimate(action, observedDifferenceMinor);

        return new EvaluationResult(
                status,
                oldMonthlyAmountMinor,
                newMonthlyAmountMinor,
                observedDifferenceMinor,
                coveredFrom,
                coveredTo,
                missingInformation,
                contradictions,
                decisionReasons,
                differenceVersusEstimate
        );
    }

    private Long computeAverageMonthlyAmount(List<SavingsActionEvidence> evidences, BigDecimal monthlyFactor) {
        if (evidences.isEmpty() || monthlyFactor == null) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (SavingsActionEvidence e : evidences) {
            total = total.add(BigDecimal.valueOf(e.getTransaction().getAmountMinor()).abs());
        }
        return total.multiply(monthlyFactor)
                .divide(BigDecimal.valueOf(evidences.size()), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BigDecimal monthlyFactor(RecurringFrequency frequency) {
        if (frequency == null) return null;
        return switch (frequency) {
            case WEEKLY -> BigDecimal.valueOf(52).divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            case MONTHLY -> BigDecimal.ONE;
            case QUARTERLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(3), 12, RoundingMode.HALF_UP);
            case YEARLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            case UNKNOWN -> null;
        };
    }

    private BigDecimal monthlyFactor(OfferBillingPeriod billingPeriod) {
        if (billingPeriod == null) return null;
        return switch (billingPeriod) {
            case WEEKLY -> BigDecimal.valueOf(52).divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            case MONTHLY -> BigDecimal.ONE;
            case QUARTERLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(3), 12, RoundingMode.HALF_UP);
            case YEARLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            case UNKNOWN -> null;
        };
    }

    private String buildDifferenceVersusEstimate(SavingsAction action, Long observedDifferenceMinor) {
        if (action.getEstimateSnapshot() == null || action.getEstimateSnapshot().potentialNetSavingsMinor() == null) {
            return "Aucune estimation initiale chiffrée disponible pour comparaison.";
        }
        int horizon = (action.getEstimateSnapshot().horizonMonths() != null && action.getEstimateSnapshot().horizonMonths() > 0)
                ? action.getEstimateSnapshot().horizonMonths() : 12;
        long estimatedMonthlySavings = action.getEstimateSnapshot().potentialNetSavingsMinor() / horizon;
        if (observedDifferenceMinor == null) {
            return String.format(Locale.FRANCE,
                    "Économie mensuelle estimée : %.2f €/mois. Données bancaires réelles insuffisantes pour calculer un écart observé.",
                    estimatedMonthlySavings / 100.0);
        }

        Long switchingCostMinor = action.getEstimateSnapshot().switchingCostMinor();
        String switchingCostNote = switchingCostMinor != null && switchingCostMinor > 0
                ? String.format(Locale.FRANCE, " Frais de changement estimés : %.2f € sur l'horizon, non déduits des paiements observés.",
                        switchingCostMinor / 100.0)
                : "";
        return String.format(Locale.FRANCE,
                "Économie mensuelle nette estimée : %.2f €/mois | Écart brut entre montants des paiements associés : %.2f €/mois. Ce montant brut n'est pas une économie nette constatée et n'est pas annualisé.%s",
                estimatedMonthlySavings / 100.0,
                observedDifferenceMinor / 100.0,
                switchingCostNote);
    }

    private boolean containsKeyword(String source, String keyword) {
        if (source == null || keyword == null || keyword.isBlank() || keyword.length() < 3) {
            return false;
        }
        return source.contains(keyword);
    }

    private String cleanString(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
