package com.eparly.api.insights.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Types internes du moteur d'analyse. */
public final class InsightModels {
    /** Categorie systeme "Virements" : l'import type tout debit en EXPENSE, y compris les virements internes. */
    public static final UUID TRANSFER_CATEGORY = UUID.fromString("550e8400-e001-0000-0000-00000000000b");

    private InsightModels() {
    }

    public enum Flow { EXPENSE, REFUND, INCOME, TRANSFER, UNKNOWN }

    public enum MonthCoverage { COMPLETE, PARTIAL, NO_DATA }

    public enum Confidence { LOW, MEDIUM, HIGH }

    public enum Priority { LOW, MEDIUM, HIGH }

    public enum DataSufficiency { NO_DATA, LIMITED_HISTORY, SUFFICIENT }

    public enum Trend { RISING, FALLING, STABLE, INSUFFICIENT_HISTORY }

    public enum AnomalyType {
        UNUSUAL_AMOUNT, CATEGORY_SPIKE, POSSIBLE_DUPLICATE, MERCHANT_AMOUNT_INCREASE, RECURRING_AMOUNT_VARIATION
    }

    public enum RecommendationType {
        REDUCE_SPENDING, SAVINGS_POTENTIAL, BUDGET_OVERRUN, BUDGET_LIMIT_HIGH, BUDGET_MARGIN_SHRINKING,
        RECURRING_INCREASE, RECURRING_SHARE, RECURRING_OVERLAP,
        POST_CANCELLATION_CHARGE, CANCELLATION_UNFINISHED, SUBSCRIPTION_RENEWAL, SUBSCRIPTION_SAVINGS
    }

    public enum RecommendationStatus { NEW, VIEWED, IGNORED, DONE }

    public enum AlternativesStatus { NONE_AVAILABLE, AVAILABLE_FOR_COMPARISON }

    /** Transaction lue pour l'analyse ; seriesId vaut null hors serie recurrente non ecartee. */
    public record Row(UUID id, UUID accountId, LocalDate date, long amountMinor, String currency, String label,
                      UUID categoryId, String categoryName, String type, UUID seriesId) {
        public Flow flow() {
            if ("TRANSFER".equals(type) || TRANSFER_CATEGORY.equals(categoryId)) return Flow.TRANSFER;
            if (("EXPENSE".equals(type) || "FEE".equals(type)) && amountMinor < 0) return Flow.EXPENSE;
            if ("REFUND".equals(type) && amountMinor > 0) return Flow.REFUND;
            if ("INCOME".equals(type) && amountMinor > 0) return Flow.INCOME;
            return Flow.UNKNOWN;
        }

        public long absolute() {
            return Math.abs(amountMinor);
        }
    }

    public record Series(UUID id, String label, long monthlyEquivalentMinor, String subscriptionName) {
    }

    public record Budget(UUID id, UUID categoryId, String categoryName, long monthlyLimitMinor) {
    }
}
