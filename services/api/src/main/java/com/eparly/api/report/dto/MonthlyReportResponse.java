package com.eparly.api.report.dto;

import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.verification.domain.EvidenceProvenance;
import com.eparly.api.savings.verification.domain.EvidenceRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record MonthlyReportResponse(
        Period period,
        FinancialSummary financialSummary,
        List<CategoryBreakdown> categoryBreakdown,
        PreviousMonthComparison previousMonthComparison,
        SubscriptionsSummary subscriptionsSummary,
        SavingsActionsSummary savingsActionsSummary,
        VerificationSummary verificationSummary,
        DataQuality dataQuality,
        Instant generatedAt
) {
    public record Period(YearMonth month, LocalDate startDate, LocalDate endDate) {
    }

    public record FinancialSummary(
            String currency,
            long totalExpensesMinor,
            long confirmedIncomeMinor,
            long uncategorizedExpensesMinor,
            long unclassifiedPositiveMinor,
            long unclassifiedNegativeMinor,
            long transactionCount
    ) {
    }

    public record CategoryBreakdown(
            UUID categoryId,
            String categoryName,
            long amountMinor,
            long transactionCount,
            BigDecimal percentage
    ) {
    }

    public record PreviousMonthComparison(
            long currentExpensesMinor,
            long previousExpensesMinor,
            long changeMinor,
            BigDecimal changePercentage,
            String coverageWarning
    ) {
    }

    public record SubscriptionsSummary(
            long activeDeclaredCount,
            long pausedDeclaredCount,
            long canceledDeclaredCount,
            long unknownStatusCount,
            BigDecimal declaredMonthlyEquivalentMinor,
            BigDecimal declaredAnnualEquivalentMinor,
            BigDecimal observedMonthlyEquivalentMinor,
            BigDecimal observedAnnualEquivalentMinor,
            long costsUnavailableCount,
            long nonEurSubscriptionCount,
            List<SubscriptionDue> indicativeDueDates
    ) {
    }

    public record SubscriptionDue(
            UUID subscriptionId,
            String name,
            LocalDate date,
            String source
    ) {
    }

    public record SavingsActionsSummary(
            long plannedCount,
            long committedCount,
            long declaredCount,
            long canceledCount,
            List<SavingsOpportunity> opportunities
    ) {
    }

    public record SavingsOpportunity(
            UUID actionId,
            UUID subscriptionId,
            String subscriptionName,
            ActionStatus status,
            Long potentialNetSavingsMinor,
            String currency,
            Integer horizonMonths,
            Instant estimatedAt,
            String obsolescence,
            VerificationStatus verificationStatus
    ) {
    }

    public record VerificationSummary(
            long notVerifiedCount,
            long partiallyVerifiedCount,
            long verifiedObservedCount,
            Long observedSavingsMinor,
            String observedSavingsExplanation,
            List<ActionVerification> actions
    ) {
    }

    public record ActionVerification(
            UUID actionId,
            VerificationStatus status,
            String currency,
            List<ObservedPayment> observedPayments,
            Long observedOldMonthlyAmountMinor,
            Long observedNewMonthlyAmountMinor,
            Long observedMonthlyDifferenceMinorGross,
            LocalDate coveredFrom,
            LocalDate coveredTo,
            List<String> contradictions,
            List<String> missingInformation
    ) {
    }

    public record ObservedPayment(
            LocalDate bookingDate,
            Long amountMinor,
            String currency,
            EvidenceRole role,
            EvidenceProvenance provenance
    ) {
    }

    public record DataQuality(
            boolean basedOnCsvImports,
            List<String> sourceTypes,
            LocalDate firstObservedBookingDate,
            LocalDate lastObservedBookingDate,
            long transactionCountIncluded,
            long accountCountIncluded,
            long excludedCurrencyTransactionCount,
            long incompatibleAccountCount,
            List<String> currenciesObserved,
            boolean noTransactionsRecorded,
            String coverageStatus,
            List<String> warnings
    ) {
    }
}
