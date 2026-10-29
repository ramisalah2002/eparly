package com.eparly.api.report.service;

import com.eparly.api.dashboard.dto.DashboardSummaryResponse;
import com.eparly.api.dashboard.dto.SpendingByCategoryResponse;
import com.eparly.api.report.dto.MonthlyReportResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyReportAssemblerTest {

    private final MonthlyReportAssembler assembler = new MonthlyReportAssembler();
    private final Instant fixedGeneratedAt = Instant.parse("2026-10-29T03:00:00Z");

    @Test
    void assemblesExactBigDecimalCategorySharesAndUnknownNegativeAmounts() {
        DashboardSummaryResponse current = summary(10_000, 50_000, 1_000, 2_500, 5);
        DashboardSummaryResponse previous = summary(8_000, 0, 0, 0, 1);
        List<SpendingByCategoryResponse> categories = List.of(
                new SpendingByCategoryResponse(null, "Non catégorisé", 1_000L, 1L, 10.0),
                new SpendingByCategoryResponse(null, "Courses", 9_000L, 2L, 90.0)
        );

        MonthlyReportResponse response = assembler.assemble(
                YearMonth.of(2026, 10),
                current,
                categories,
                previous,
                coverage(5, 5, 0, 2, 0, 300, "CSV", "EUR", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                fixedGeneratedAt
        );

        assertThat(response.generatedAt()).isEqualTo(fixedGeneratedAt);
        assertThat(response.financialSummary().totalExpensesMinor()).isEqualTo(10_000);
        assertThat(response.financialSummary().confirmedIncomeMinor()).isEqualTo(50_000);
        assertThat(response.financialSummary().unclassifiedNegativeMinor()).isEqualTo(300);
        assertThat(response.categoryBreakdown()).extracting(MonthlyReportResponse.CategoryBreakdown::percentage)
                .containsExactly(new java.math.BigDecimal("10.00"), new java.math.BigDecimal("90.00"));
        assertThat(response.previousMonthComparison().changeMinor()).isEqualTo(2_000);
        assertThat(response.previousMonthComparison().changePercentage()).isEqualByComparingTo("25.00");
        assertThat(response.verificationSummary().observedSavingsMinor()).isNull();
    }

    @Test
    void emptyCurrentAndPreviousMonthsDoNotInventCoverageOrPercentage() {
        DashboardSummaryResponse empty = summary(0, 0, 0, 0, 0);

        MonthlyReportResponse response = assembler.assemble(
                YearMonth.of(2026, 10),
                empty,
                List.of(),
                empty,
                coverage(0, 0, 0, 0, 0, 0, "", "", null, null),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                fixedGeneratedAt
        );

        assertThat(response.categoryBreakdown()).isEmpty();
        assertThat(response.previousMonthComparison().changePercentage()).isNull();
        assertThat(response.dataQuality().noTransactionsRecorded()).isTrue();
        assertThat(response.dataQuality().coverageStatus()).isEqualTo("UNKNOWN");
        assertThat(response.dataQuality().warnings()).anyMatch(message -> message.contains("ne signifie pas"));
        assertThat(response.verificationSummary().observedSavingsMinor()).isNull();
    }

    private static DashboardSummaryResponse summary(
            long expenses,
            long income,
            long unclassifiedExpenses,
            long unclassifiedPositive,
            long transactionCount
    ) {
        return new DashboardSummaryResponse(
                expenses, income, unclassifiedExpenses, unclassifiedPositive,
                transactionCount, 0L, null, null, "EUR", true
        );
    }

    private static Map<String, Object> coverage(
            long allCount,
            long eurCount,
            long excludedCount,
            long accountCount,
            long incompatibleAccountCount,
            long unclassifiedNegative,
            String sourceTypes,
            String currencies,
            LocalDate firstDate,
            LocalDate lastDate
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("all_transaction_count", allCount);
        values.put("eur_transaction_count", eurCount);
        values.put("excluded_currency_transaction_count", excludedCount);
        values.put("included_account_count", accountCount);
        values.put("incompatible_account_count", incompatibleAccountCount);
        values.put("unclassified_negative", unclassifiedNegative);
        values.put("first_observed_date", firstDate);
        values.put("last_observed_date", lastDate);
        values.put("source_types", sourceTypes);
        values.put("currencies", currencies);
        return values;
    }
}
