package com.eparly.api.savings.verification.service;

import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.verification.domain.EvidenceRole;
import com.eparly.api.savings.verification.dto.CandidateTransactionResponse;
import com.eparly.api.savings.verification.dto.VerificationCandidatesResponse;
import com.eparly.api.savings.verification.repository.SavingsActionEvidenceRepository;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.repository.TransactionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SavingsTransactionMatchingService {

    private final TransactionRepository transactionRepository;
    private final SavingsActionEvidenceRepository evidenceRepository;
    private final OfferPricingRepository offerPricingRepository;

    public SavingsTransactionMatchingService(
            TransactionRepository transactionRepository,
            SavingsActionEvidenceRepository evidenceRepository,
            OfferPricingRepository offerPricingRepository) {
        this.transactionRepository = transactionRepository;
        this.evidenceRepository = evidenceRepository;
        this.offerPricingRepository = offerPricingRepository;
    }

    public VerificationCandidatesResponse findCandidates(UUID userId, SavingsAction action, EvidenceRole roleFilter, Pageable pageable) {
        Subscription subscription = action.getSubscription();
        CommercialOffer alternativeOffer = action.getAlternativeOffer();

        // 1. Récupérer toutes les transactions du propriétaire ordonnées par date
        List<Transaction> userTransactions = transactionRepository.findAllByUserIdOrderByBookingDateDesc(userId);

        // 2. Déterminer les montants cibles attendus
        Long expectedOldAmount = subscription.getDeclaredAmountMinor();
        if (expectedOldAmount == null && subscription.getRecurringSeries() != null) {
            expectedOldAmount = subscription.getRecurringSeries().getUsualAmountMinor();
        }

        Long expectedNewAmount = null;
        if (action.getEstimateSnapshot() != null && action.getEstimateSnapshot().alternativeTotalCostMinor() != null) {
            int horizon = (action.getEstimateSnapshot().horizonMonths() != null && action.getEstimateSnapshot().horizonMonths() > 0)
                    ? action.getEstimateSnapshot().horizonMonths() : 12;
            expectedNewAmount = action.getEstimateSnapshot().alternativeTotalCostMinor() / horizon;
        } else {
            Optional<OfferPricing> pricing = offerPricingRepository.findByOfferId(alternativeOffer.getId());
            if (pricing.isPresent()) {
                expectedNewAmount = pricing.get().getAmountMinor();
            }
        }

        LocalDate cutoffDate = action.getEffectiveChangeDate();

        String oldProviderName = cleanString(subscription.getProvider());
        String oldSubName = cleanString(subscription.getName());
        String newProviderName = cleanString(alternativeOffer.getProvider() != null ? alternativeOffer.getProvider().getName() : "");
        String newCommercialName = cleanString(alternativeOffer.getCommercialName());

        List<CandidateTransactionResponse> candidates = new ArrayList<>();

        for (Transaction tx : userTransactions) {
            if (subscription.getAccount() != null
                    && !subscription.getAccount().getId().equals(tx.getAccount().getId())) {
                continue;
            }

            // Règle absolue : uniquement les dépenses/frais, exclusion stricte des virements, revenus et remboursements
            if (!tx.isExpense()) {
                continue;
            }

            // Règle d'unicité : exclusion des transactions déjà utilisées comme preuve (toutes actions confondues)
            if (evidenceRepository.existsByTransactionId(tx.getId())) {
                continue;
            }

            String txLabel = cleanString(tx.getNormalizedLabel());
            long txAmountAbs = Math.abs(tx.getAmountMinor());
            LocalDate bookingDate = tx.getBookingDate();

            // Évaluation score OLD
            int oldScore = 0;
            List<String> oldReasons = new ArrayList<>();
            if (containsKeyword(txLabel, oldProviderName) || containsKeyword(txLabel, oldSubName)) {
                oldScore += 40;
                oldReasons.add("Libellé compatible avec l'ancien fournisseur / service");
            }
            if (expectedOldAmount != null) {
                if (txAmountAbs == expectedOldAmount) {
                    oldScore += 35;
                    oldReasons.add("Montant strictement identique à l'ancien abonnement (" + formatEuros(expectedOldAmount) + ")");
                } else if (isCloseAmount(txAmountAbs, expectedOldAmount)) {
                    oldScore += 20;
                    oldReasons.add("Montant proche de l'ancien abonnement (" + formatEuros(expectedOldAmount) + ")");
                }
            }
            if (cutoffDate != null && !bookingDate.isAfter(cutoffDate)) {
                oldScore += 15;
                oldReasons.add("Date antérieure ou égale à la date de bascule");
            }
            if (subscription.getRecurringSeries() != null
                    && subscription.getRecurringSeries().getNormalizedLabel() != null
                    && txLabel.contains(cleanString(subscription.getRecurringSeries().getNormalizedLabel()))) {
                oldScore += 10;
                oldReasons.add("Correspond à la série récurrente de l'ancien abonnement");
            }

            // Évaluation score NEW
            int newScore = 0;
            List<String> newReasons = new ArrayList<>();
            if (containsKeyword(txLabel, newProviderName) || containsKeyword(txLabel, newCommercialName)) {
                newScore += 40;
                newReasons.add("Libellé compatible avec le nouveau fournisseur / service (" + alternativeOffer.getCommercialName() + ")");
            }
            if (expectedNewAmount != null) {
                if (txAmountAbs == expectedNewAmount) {
                    newScore += 35;
                    newReasons.add("Montant strictement identique à la nouvelle offre (" + formatEuros(expectedNewAmount) + ")");
                } else if (isCloseAmount(txAmountAbs, expectedNewAmount)) {
                    newScore += 20;
                    newReasons.add("Montant proche de la nouvelle offre (" + formatEuros(expectedNewAmount) + ")");
                }
            }
            if (cutoffDate != null && !bookingDate.isBefore(cutoffDate)) {
                newScore += 15;
                newReasons.add("Date postérieure ou égale à la date de bascule");
            }

            EvidenceRole suggestedRole = (newScore > oldScore) ? EvidenceRole.NEW_SUBSCRIPTION_PAYMENT : EvidenceRole.OLD_SUBSCRIPTION_PAYMENT;
            int maxScore = Math.max(newScore, oldScore);
            List<String> matchingReasons = (suggestedRole == EvidenceRole.NEW_SUBSCRIPTION_PAYMENT) ? newReasons : oldReasons;

            if (maxScore >= 30) {
                if (roleFilter != null && suggestedRole != roleFilter) {
                    continue;
                }
                candidates.add(new CandidateTransactionResponse(
                        tx.getId(),
                        tx.getBookingDate(),
                        tx.getAmountMinor(),
                        tx.getCurrency(),
                        tx.getRawLabel(),
                        tx.getNormalizedLabel(),
                        tx.getAccount().getId(),
                        suggestedRole,
                        matchingReasons,
                        Math.min(100, maxScore)
                ));
            }
        }

        // Tri : meilleur score d'abord, puis date d'opération la plus récente
        candidates.sort(Comparator.comparingInt(CandidateTransactionResponse::confidenceScore).reversed()
                .thenComparing(CandidateTransactionResponse::bookingDate, Comparator.reverseOrder()));

        int total = candidates.size();
        int page = pageable != null ? pageable.getPageNumber() : 0;
        int size = pageable != null && pageable.getPageSize() > 0 ? pageable.getPageSize() : 20;
        int totalPages = (int) Math.ceil((double) total / size);

        int fromIndex = (int) Math.min((long) page * size, total);
        int toIndex = (int) Math.min((long) fromIndex + size, total);
        List<CandidateTransactionResponse> paged = candidates.subList(fromIndex, toIndex);

        return new VerificationCandidatesResponse(paged, page, size, total, totalPages);
    }

    private boolean containsKeyword(String source, String keyword) {
        if (source == null || keyword == null || keyword.isBlank() || keyword.length() < 3) {
            return false;
        }
        return source.contains(keyword);
    }

    private boolean isCloseAmount(long a, long b) {
        if (b == 0) return false;
        double ratio = (double) a / b;
        return ratio >= 0.85 && ratio <= 1.15;
    }

    private String cleanString(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatEuros(Long minor) {
        if (minor == null) return "0.00 €";
        return String.format(Locale.FRANCE, "%.2f €", minor / 100.0);
    }
}
