package com.eparly.api.report.service;

import com.eparly.api.dashboard.dto.DashboardSummaryResponse;
import com.eparly.api.dashboard.dto.SpendingByCategoryResponse;
import com.eparly.api.report.dto.MonthlyReportResponse;
import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.action.dto.SavingsEstimateSnapshot;
import com.eparly.api.savings.verification.domain.SavingsActionEvidence;
import com.eparly.api.savings.verification.domain.SavingsActionVerification;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.dto.SubscriptionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.eparly.api.report.dto.MonthlyReportResponse.*;

@Component
public class MonthlyReportAssembler {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    public MonthlyReportResponse assemble(
            YearMonth month,
            DashboardSummaryResponse current,
            List<SpendingByCategoryResponse> dashboardCategories,
            DashboardSummaryResponse previous,
            Map<String, Object> coverage,
            List<SubscriptionResponse> subscriptions,
            List<SavingsAction> savingsActions,
            List<SavingsActionVerification> verifications,
            List<SavingsActionEvidence> evidences,
            Instant generatedAt
    ) {
        List<CategoryBreakdown> categories = categories(dashboardCategories, current.totalExpensesMinor());
        List<SavingsOpportunity> opportunities = opportunities(savingsActions, generatedAt);
        Map<UUID, SavingsActionVerification> latestVerificationByAction = latestVerifications(verifications);
        Map<UUID, List<SavingsActionEvidence>> evidencesByAction = evidences.stream()
                .collect(Collectors.groupingBy(evidence -> evidence.getAction().getId()));

        return new MonthlyReportResponse(
                new Period(month, month.atDay(1), month.atEndOfMonth()),
                new FinancialSummary(
                        "EUR",
                        current.totalExpensesMinor(),
                        current.confirmedIncomeMinor(),
                        current.unclassifiedExpensesMinor(),
                        current.unclassifiedPositiveMinor(),
                        number(coverage.get("unclassified_negative")),
                        current.transactionsCount()
                ),
                categories,
                comparison(current, previous),
                subscriptions(subscriptions),
                savingsActions(savingsActions, opportunities),
                verifications(savingsActions, latestVerificationByAction, evidencesByAction),
                dataQuality(current, coverage),
                generatedAt
        );
    }

    private List<CategoryBreakdown> categories(
            List<SpendingByCategoryResponse> rows,
            long totalExpensesMinor
    ) {
        return rows.stream()
                .map(row -> new CategoryBreakdown(
                        row.categoryId(),
                        row.categoryName(),
                        row.amountMinor(),
                        row.transactionCount(),
                        totalExpensesMinor == 0
                                ? BigDecimal.ZERO.setScale(2)
                                : BigDecimal.valueOf(row.amountMinor())
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(BigDecimal.valueOf(totalExpensesMinor), 2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private PreviousMonthComparison comparison(
            DashboardSummaryResponse current,
            DashboardSummaryResponse previous
    ) {
        long currentExpenses = current.totalExpensesMinor();
        long previousExpenses = previous.totalExpensesMinor();
        long change = currentExpenses - previousExpenses;
        BigDecimal percentage = previous.transactionsCount() == 0 || previousExpenses == 0
                ? null
                : BigDecimal.valueOf(change)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(previousExpenses), 2, RoundingMode.HALF_UP);

        return new PreviousMonthComparison(
                currentExpenses,
                previousExpenses,
                change,
                percentage,
                "La comparaison porte uniquement sur les transactions EUR enregistrées; la couverture des imports est inconnue."
        );
    }

    private SubscriptionsSummary subscriptions(List<SubscriptionResponse> subscriptions) {
        long active = 0;
        long paused = 0;
        long canceled = 0;
        long unknown = 0;
        long unavailable = 0;
        long nonEur = 0;
        BigDecimal declaredMonthly = BigDecimal.ZERO;
        BigDecimal observedMonthly = BigDecimal.ZERO;
        List<SubscriptionDue> dueDates = new ArrayList<>();

        for (SubscriptionResponse subscription : subscriptions) {
            SubscriptionStatus status = subscription.declaredStatus();
            if (status == SubscriptionStatus.ACTIVE) active++;
            else if (status == SubscriptionStatus.PAUSED) paused++;
            else if (status == SubscriptionStatus.CANCELED) canceled++;
            else unknown++;

            if (!"EUR".equalsIgnoreCase(subscription.currency())) {
                nonEur++;
                continue;
            }

            boolean declaredCostAvailable = false;
            if (status == SubscriptionStatus.ACTIVE
                    && subscription.declaredAmountMinor() != null
                    && subscription.declaredFrequency() != null) {
                BigDecimal monthly = monthlyEquivalent(
                        BigDecimal.valueOf(subscription.declaredAmountMinor()),
                        subscription.declaredFrequency().name()
                );
                if (monthly != null) {
                    declaredMonthly = declaredMonthly.add(monthly);
                    declaredCostAvailable = true;
                }
            }

            if (status == SubscriptionStatus.ACTIVE && subscription.observedMonthlyEquivalentMinor() != null) {
                observedMonthly = observedMonthly.add(subscription.observedMonthlyEquivalentMinor());
            }

            if (status == SubscriptionStatus.ACTIVE && !declaredCostAvailable
                    && subscription.observedMonthlyEquivalentMinor() == null) {
                unavailable++;
            }

            if (status == SubscriptionStatus.ACTIVE) {
                LocalDate declaredDueDate = subscription.nextDueDate();
                LocalDate estimatedDueDate = subscription.indicativeNextDueDate();
                if (declaredDueDate != null) {
                    dueDates.add(new SubscriptionDue(subscription.id(), subscription.name(), declaredDueDate, "DECLARED"));
                } else if (estimatedDueDate != null) {
                    dueDates.add(new SubscriptionDue(subscription.id(), subscription.name(), estimatedDueDate, "OBSERVED_ESTIMATE"));
                }
            }
        }

        return new SubscriptionsSummary(
                active,
                paused,
                canceled,
                unknown,
                scale(declaredMonthly),
                scale(declaredMonthly.multiply(TWELVE)),
                scale(observedMonthly),
                scale(observedMonthly.multiply(TWELVE)),
                unavailable,
                nonEur,
                List.copyOf(dueDates)
        );
    }

    private BigDecimal monthlyEquivalent(BigDecimal amountMinor, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> amountMinor.multiply(BigDecimal.valueOf(52))
                    .divide(TWELVE, 4, RoundingMode.HALF_UP);
            case "MONTHLY" -> amountMinor;
            case "QUARTERLY" -> amountMinor.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            case "YEARLY" -> amountMinor.divide(TWELVE, 4, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    private SavingsActionsSummary savingsActions(
            List<SavingsAction> actions,
            List<SavingsOpportunity> opportunities
    ) {
        return new SavingsActionsSummary(
                count(actions, ActionStatus.PLANNED),
                count(actions, ActionStatus.COMMITTED),
                count(actions, ActionStatus.DECLARED),
                count(actions, ActionStatus.CANCELED),
                opportunities
        );
    }

    private List<SavingsOpportunity> opportunities(List<SavingsAction> actions, Instant generatedAt) {
        return actions.stream()
                .map(action -> {
                    SavingsEstimateSnapshot snapshot = action.getEstimateSnapshot();
                    String obsolescence;
                    if (snapshot == null || snapshot.offerVerifiedAt() == null) {
                        obsolescence = "UNKNOWN";
                    } else if (snapshot.offerVerifiedAt().isBefore(generatedAt.minusSeconds(30L * 24 * 60 * 60))) {
                        obsolescence = "STALE";
                    } else {
                        obsolescence = "CURRENT";
                    }
                    return new SavingsOpportunity(
                            action.getId(),
                            action.getSubscription().getId(),
                            action.getSubscription().getName(),
                            action.getStatus(),
                            snapshot != null ? snapshot.potentialNetSavingsMinor() : null,
                            snapshot != null ? snapshot.currency() : action.getSubscription().getCurrency(),
                            snapshot != null ? snapshot.horizonMonths() : action.getHorizonMonths(),
                            snapshot != null ? snapshot.estimatedAt() : null,
                            obsolescence,
                            action.getVerificationStatus()
                    );
                })
                .toList();
    }

    private VerificationSummary verifications(
            List<SavingsAction> actions,
            Map<UUID, SavingsActionVerification> latestByAction,
            Map<UUID, List<SavingsActionEvidence>> evidencesByAction
    ) {
        List<ActionVerification> details = actions.stream()
                .map(action -> {
                    UUID actionId = action.getId();
                    SavingsActionVerification verification = latestByAction.get(actionId);
                    List<ObservedPayment> payments = evidencesByAction.getOrDefault(actionId, List.of()).stream()
                            .map(evidence -> new ObservedPayment(
                                    evidence.getTransaction().getBookingDate(),
                                    evidence.getTransaction().getAmountMinor(),
                                    evidence.getTransaction().getCurrency(),
                                    evidence.getRole(),
                                    evidence.getProvenance()
                            ))
                            .toList();
                    return new ActionVerification(
                            actionId,
                            verification != null ? verification.getStatus() : action.getVerificationStatus(),
                            action.getEstimateSnapshot() != null
                                    ? action.getEstimateSnapshot().currency()
                                    : action.getSubscription().getCurrency(),
                            payments,
                            verification != null ? verification.getOldMonthlyAmountMinor() : null,
                            verification != null ? verification.getNewMonthlyAmountMinor() : null,
                            verification != null ? verification.getObservedDifferenceMinor() : null,
                            verification != null ? verification.getCoveredFrom() : null,
                            verification != null ? verification.getCoveredTo() : null,
                            verification != null ? immutable(verification.getContradictions()) : List.of(),
                            verification != null ? immutable(verification.getMissingInformation()) : List.of()
                    );
                })
                .toList();

        long notVerified = details.stream().filter(v -> v.status() == VerificationStatus.NOT_VERIFIED).count();
        long partial = details.stream().filter(v -> v.status() == VerificationStatus.PARTIALLY_VERIFIED).count();
        long observed = details.stream().filter(v -> v.status() == VerificationStatus.VERIFIED_OBSERVED).count();
        return new VerificationSummary(
                notVerified,
                partial,
                observed,
                null,
                "Aucune économie nette réellement observée n'est certifiée par les preuves actuellement disponibles.",
                details
        );
    }

    private Map<UUID, SavingsActionVerification> latestVerifications(List<SavingsActionVerification> verifications) {
        Map<UUID, SavingsActionVerification> latest = new HashMap<>();
        for (SavingsActionVerification verification : verifications) {
            latest.putIfAbsent(verification.getAction().getId(), verification);
        }
        return latest;
    }

    private DataQuality dataQuality(DashboardSummaryResponse summary, Map<String, Object> coverage) {
        List<String> sourceTypes = split(stringValue(coverage.get("source_types")));
        List<String> currencies = split(stringValue(coverage.get("currencies")));
        long allTransactions = number(coverage.get("all_transaction_count"));
        long eurTransactions = number(coverage.get("eur_transaction_count"));
        long excludedCurrencies = number(coverage.get("excluded_currency_transaction_count"));
        long accounts = number(coverage.get("included_account_count"));
        long incompatibleAccounts = number(coverage.get("incompatible_account_count"));
        List<String> warnings = new ArrayList<>();
        warnings.add("La couverture bancaire est inconnue; les CSV ne prouvent pas l'import de l'historique complet.");
        if (allTransactions == 0) {
            warnings.add("Aucune transaction enregistrée pour cette période; cela ne signifie pas qu'aucune dépense n'a eu lieu.");
        }
        if (excludedCurrencies > 0 || incompatibleAccounts > 0) {
            warnings.add("Les transactions hors EUR sont exclues des agrégats communs.");
        }
        if (summary.unclassifiedExpensesMinor() > 0
                || summary.unclassifiedPositiveMinor() > 0
                || number(coverage.get("unclassified_negative")) > 0) {
            warnings.add("Des dépenses non catégorisées ou des opérations UNKNOWN/REFUND non qualifiées sont présentes.");
        }

        return new DataQuality(
                sourceTypes.contains("CSV"),
                sourceTypes,
                localDate(coverage.get("first_observed_date")),
                localDate(coverage.get("last_observed_date")),
                eurTransactions,
                accounts,
                excludedCurrencies,
                incompatibleAccounts,
                currencies,
                allTransactions == 0,
                "UNKNOWN",
                List.copyOf(warnings)
        );
    }

    private long count(List<SavingsAction> actions, ActionStatus status) {
        return actions.stream().filter(action -> action.getStatus() == status).count();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static List<String> immutable(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(",\\s*")).filter(part -> !part.isBlank()).toList();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw new IllegalStateException("Expected numeric monthly report aggregate");
    }

    private static LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        throw new IllegalStateException("Expected date monthly report aggregate");
    }
}
