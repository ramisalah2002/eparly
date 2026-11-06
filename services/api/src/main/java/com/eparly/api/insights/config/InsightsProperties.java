package com.eparly.api.insights.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Seuils du moteur d'analyse. Les montants sont en centimes.
 *
 * @param lookbackMonths      historique lu avant le mois analyse
 * @param minHistoryMonths    mois complets exiges avant toute comparaison
 * @param minObservations     observations exigees avant de declarer un montant inhabituel
 * @param reducibleCategories categories non essentielles, par nom : les seules ou une reduction est suggeree
 */
@ConfigurationProperties(prefix = "eparly.insights")
public record InsightsProperties(
        @DefaultValue("12") int lookbackMonths,
        @DefaultValue("3") int minHistoryMonths,
        @DefaultValue("5") int minObservations,
        @DefaultValue("5") int partialMonthToleranceDays,
        @DefaultValue("20") int minChangePercent,
        @DefaultValue("1000") long minChangeMinor,
        @DefaultValue("3") int unusualMedianMultiplier,
        @DefaultValue("3") int unusualMadMultiplier,
        @DefaultValue("5000") long minUnusualAmountMinor,
        @DefaultValue("1") int duplicateWindowDays,
        @DefaultValue("500") long minDuplicateAmountMinor,
        @DefaultValue("5") int priceIncreasePercent,
        @DefaultValue("100") long minPriceIncreaseMinor,
        @DefaultValue("4") int frequentMerchantPerMonth,
        @DefaultValue("40") int weekdayConcentrationPercent,
        @DefaultValue("20") int weekdayMinTransactions,
        @DefaultValue("15") int recurringSharePercent,
        @DefaultValue("10") int savingsGoalPercent,
        @DefaultValue("20") int reductionCapPercent,
        @DefaultValue("500") long minSavingsGoalMinor,
        @DefaultValue("2") int budgetOverrunMonths,
        @DefaultValue("50") int budgetLowUsagePercent,
        @DefaultValue("80") int budgetTightUsagePercent,
        @DefaultValue("P30D") Duration hideDuration,
        @DefaultValue({"Restaurants", "Shopping", "Loisirs"}) List<String> reducibleCategories
) {
}
