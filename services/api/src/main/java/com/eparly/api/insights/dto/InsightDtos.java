package com.eparly.api.insights.dto;

import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.insights.domain.InsightModels.AlternativesStatus;
import com.eparly.api.insights.domain.InsightModels.AnomalyType;
import com.eparly.api.insights.domain.InsightModels.Confidence;
import com.eparly.api.insights.domain.InsightModels.DataSufficiency;
import com.eparly.api.insights.domain.InsightModels.MonthCoverage;
import com.eparly.api.insights.domain.InsightModels.Priority;
import com.eparly.api.insights.domain.InsightModels.RecommendationStatus;
import com.eparly.api.insights.domain.InsightModels.RecommendationType;
import com.eparly.api.insights.domain.InsightModels.Trend;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrat JSON de /v1/insights et /v1/budgets. Montants en centimes d'euros, valeurs positives. */
public final class InsightDtos {
    public static final String METHOD = "DETERMINISTIC_STATISTICS";

    private InsightDtos() {
    }

    @Schema(description = "Fiabilite des donnees ; basedOnIncompleteData reste vrai car les imports peuvent etre partiels")
    public record DataQuality(
            DataSufficiency sufficiency, MonthCoverage monthCoverage, int comparableMonths,
            LocalDate firstTransactionDate, LocalDate lastTransactionDate,
            long uncategorizedExpenses, long unknownTransactions, long unknownDebitsMinor, long unknownCreditsMinor,
            long excludedTransfers,
            @Schema(description = "Transactions hors EUR, non converties, par devise") Map<String, Long> excludedCurrencies,
            boolean basedOnIncompleteData,
            @Schema(description = "Toujours un calcul statistique, jamais un modele d'IA") String analysisMethod) {
    }

    public record CategorySpend(UUID categoryId, @Schema(description = "null : sans categorie") String categoryName,
                                long amountMinor, long transactionCount, int sharePercent) {
    }

    public record TopExpense(UUID transactionId, LocalDate date, String label, String categoryName, long amountMinor) {
    }

    @Schema(description = "Une transaction est fixe si elle appartient a une serie recurrente non ecartee")
    public record FixedVariable(long fixedMinor, long variableMinor) {
    }

    public record OverviewResponse(
            String month, long expensesMinor, String previousMonth, Long previousMonthExpensesMinor,
            @Schema(description = "null si un des deux mois n'est pas complet, ou si le precedent vaut zero") Integer changePercent,
            @Schema(description = "Moyenne des mois complets ; null sans historique suffisant") Long averageMonthlyExpensesMinor,
            int averageBasedOnMonths, long refundsMinor,
            @Schema(description = "Revenus confirmes uniquement ; un credit non qualifie n'en est jamais un") long confirmedIncomeMinor,
            @Schema(description = "null si le mois est partiel ou sans revenu confirme") Long periodBalanceMinor,
            FixedVariable fixedVariable, List<CategorySpend> byCategory, List<TopExpense> topExpenses, DataQuality dataQuality) {
    }

    public record MonthPoint(String month, MonthCoverage coverage, long expensesMinor, long refundsMinor,
                             long confirmedIncomeMinor, long transactionCount) {
    }

    public record CategoryTrend(UUID categoryId, String categoryName, Trend trend, Integer changePercent, boolean nonEssential,
                                List<Long> completeMonthsMinor, Long averageMinor) {
    }

    public record FrequentMerchant(String label, double transactionsPerMonth, long totalMinor, long transactionCount) {
    }

    public record WeekdayConcentration(String dayOfWeek, int sharePercent, long transactionCount) {
    }

    public record TrendsResponse(String month, List<MonthPoint> months, List<CategoryTrend> categories,
                                 List<FrequentMerchant> frequentMerchants,
                                 @Schema(description = "null si aucune concentration significative") WeekdayConcentration weekdayConcentration,
                                 DataQuality dataQuality) {
    }

    public record HistoryBasis(int observations, Long referenceAmountMinor, String description) {
    }

    public record AnomalyResponse(UUID id, AnomalyType type, Priority severity, long amountMinor, HistoryBasis history,
                                  String explanation, Confidence confidence, List<UUID> transactionIds, String month) {
    }

    public record AnomaliesResponse(PageResponse<AnomalyResponse> anomalies, DataQuality dataQuality) {
    }

    public record RecommendationResponse(
            UUID id, RecommendationType type, String title, String description, Priority priority, Confidence confidence,
            String analysisPeriod, String justification,
            @Schema(description = "Projection annuelle theorique, jamais une economie realisee") Long potentialAnnualSavingsMinor,
            String suggestedAction, Instant computedAt, List<UUID> evidenceIds, RecommendationStatus status) {
    }

    @Schema(description = "Trois notions distinctes, jamais additionnees")
    public record SavingsSummary(
            @Schema(description = "Somme theorique des objectifs proposes ici") long potentialEstimatedAnnualMinor,
            @Schema(description = "Plan d'economies existant") Long plannedMinor,
            @Schema(description = "Economie constatee ; null tant qu'elle n'est pas observee") Long observedMinor) {
    }

    @Schema(description = "Aucune offre brouillon n'est utilisee ; aucun prix n'est fabrique")
    public record CommercialAlternatives(AlternativesStatus status, int publishedOfferCount, String message) {
    }

    public record RecommendationsResponse(PageResponse<RecommendationResponse> recommendations, SavingsSummary savings,
                                          CommercialAlternatives commercialAlternatives, DataQuality dataQuality) {
    }

    public record RecommendationStatusRequest(@NotNull RecommendationStatus status) {
    }

    public record BudgetRequest(@NotNull @Min(1) @Max(100_000_000) Long monthlyLimitMinor) {
    }

    public record BudgetResponse(UUID id, UUID categoryId, String categoryName, long monthlyLimitMinor, String currency) {
    }
}
