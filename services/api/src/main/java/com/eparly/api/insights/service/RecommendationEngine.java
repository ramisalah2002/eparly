package com.eparly.api.insights.service;

import com.eparly.api.insights.config.InsightsProperties;
import com.eparly.api.insights.domain.InsightModels.Budget;
import com.eparly.api.insights.domain.InsightModels.Confidence;
import com.eparly.api.insights.domain.InsightModels.Flow;
import com.eparly.api.insights.domain.InsightModels.MonthCoverage;
import com.eparly.api.insights.domain.InsightModels.Priority;
import com.eparly.api.insights.domain.InsightModels.RecommendationStatus;
import com.eparly.api.insights.domain.InsightModels.RecommendationType;
import com.eparly.api.insights.domain.InsightModels.Row;
import com.eparly.api.insights.domain.InsightModels.Series;
import com.eparly.api.insights.dto.InsightDtos.RecommendationResponse;
import com.eparly.api.subscription.service.SubscriptionAnalysisService.Signal;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Regles deterministes ; l'identifiant depend de l'utilisateur, du type et du sujet, jamais du mois. */
@Component
public class RecommendationEngine {
    private static final int BUDGET_WINDOW_MONTHS = 3;
    private static final String SUBSCRIPTIONS_CATEGORY = "Abonnements";

    private final InsightsProperties properties;

    public RecommendationEngine(InsightsProperties properties) {
        this.properties = properties;
    }

    public List<RecommendationResponse> compute(UUID userId, SpendingSnapshot snapshot, List<Budget> budgets,
                                                List<Series> series, List<Signal> signals, Instant now) {
        List<Scored> scored = new ArrayList<>();
        Set<UUID> reducing = new HashSet<>();
        reductions(userId, snapshot, now, scored, reducing);
        savingsPotentials(userId, snapshot, now, scored, reducing);
        budgets(userId, snapshot, budgets, now, scored);
        recurring(userId, snapshot, series, now, scored);
        for (Signal signal : signals) {
            scored.add(build(userId, RecommendationType.valueOf(signal.type()), signal.subject(), signal.title(), signal.description(),
                    signal.strong() ? Confidence.HIGH : Confidence.MEDIUM, snapshot, signal.justification(), signal.annualMinor(),
                    signal.action(), now, signal.evidence(), signal.impactMinor()));
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(item -> item.recommendation().id()))
                .map(Scored::recommendation).toList();
    }

    private record Scored(RecommendationResponse recommendation, int score) {
    }

    private void reductions(UUID userId, SpendingSnapshot snapshot, Instant now, List<Scored> out, Set<UUID> reducing) {
        if (!snapshot.hasComparableHistory()) return;
        for (SpendingSnapshot.CategoryRef category : reducible(snapshot)) {
            long total = snapshot.categoryTotal(snapshot.month(), category.id());
            long average = snapshot.averageForCategory(category.id());
            long excess = total - average;
            if (average == 0 || excess < properties.minChangeMinor()
                    || InsightText.percent(excess, average) < properties.minChangePercent()) continue;
            // Objectif realiste : jamais plus qu'une fraction du poste, arrondi a l'euro inferieur.
            long goal = wholeEuros(Math.min(excess, total * properties.reductionCapPercent() / 100));
            reducing.add(category.id());
            out.add(build(userId, RecommendationType.REDUCE_SPENDING, category.id().toString(),
                    "Dépenses " + category.name() + " au-dessus de vos habitudes",
                    "Vos dépenses " + category.name() + " dépassent votre moyenne habituelle de "
                            + InsightText.euros(excess) + " ce mois-ci.",
                    Confidence.MEDIUM, snapshot,
                    InsightText.euros(total) + " ce mois-ci, contre " + InsightText.euros(average) + " en moyenne sur "
                            + snapshot.baseline().size() + " mois complets.",
                    goal * 12, "Objectif proposé : réduire ce poste de " + InsightText.euros(goal) + " par mois.",
                    now, evidence(snapshot, category.id()), excess));
        }
    }

    private void savingsPotentials(UUID userId, SpendingSnapshot snapshot, Instant now, List<Scored> out, Set<UUID> reducing) {
        if (!snapshot.hasComparableHistory()) return;
        for (SpendingSnapshot.CategoryRef category : reducible(snapshot)) {
            if (reducing.contains(category.id())) continue;
            long average = snapshot.averageForCategory(category.id());
            long goal = wholeEuros(average * properties.savingsGoalPercent() / 100);
            if (goal < properties.minSavingsGoalMinor()) continue;
            long annual = goal * 12;
            out.add(build(userId, RecommendationType.SAVINGS_POTENTIAL, category.id().toString(),
                    "Économie possible sur " + category.name(),
                    "En réduisant ce poste de " + InsightText.euros(goal) + " par mois, vous pourriez économiser "
                            + InsightText.euros(annual) + " par an.",
                    Confidence.MEDIUM, snapshot,
                    "Objectif de " + properties.savingsGoalPercent() + " % sur une moyenne de " + InsightText.euros(average)
                            + " par mois. Projection théorique, pas une économie réalisée.",
                    annual, "Décider si cet objectif vous convient.", now, List.of(), goal));
        }
    }

    private void budgets(UUID userId, SpendingSnapshot snapshot, List<Budget> budgets, Instant now, List<Scored> out) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth candidate : snapshot.window().reversed()) {
            MonthCoverage coverage = snapshot.coverage(candidate);
            if (coverage == MonthCoverage.COMPLETE || (candidate.equals(snapshot.month()) && coverage == MonthCoverage.PARTIAL)) {
                months.add(candidate);
            }
            if (months.size() == BUDGET_WINDOW_MONTHS) break;
        }
        if (months.isEmpty()) return;
        for (Budget budget : budgets) {
            List<Long> spent = months.stream().map(month -> snapshot.categoryTotal(month, budget.categoryId())).toList();
            long limit = budget.monthlyLimitMinor();
            long overruns = spent.stream().filter(value -> value > limit).count();
            long latest = spent.get(0);
            long average = Math.round(spent.stream().mapToLong(Long::longValue).average().orElse(0));
            String detail = "Limite de " + InsightText.euros(limit) + " ; dépenses récentes, du plus récent au plus ancien : "
                    + spent.stream().map(InsightText::euros).collect(Collectors.joining(", ")) + ".";
            String subject = budget.id().toString();
            boolean fullWindow = spent.size() >= BUDGET_WINDOW_MONTHS;
            if (overruns >= properties.budgetOverrunMonths()) {
                out.add(build(userId, RecommendationType.BUDGET_OVERRUN, subject,
                        "Budget " + budget.categoryName() + " régulièrement dépassé",
                        "Ce budget a été dépassé " + overruns + " fois sur les " + spent.size() + " derniers mois observés.",
                        fullWindow ? Confidence.HIGH : Confidence.MEDIUM, snapshot, detail, null,
                        "Réduire ce poste, ou revoir vous-même la limite : vos dépenses récentes sont de "
                                + InsightText.euros(average) + " en moyenne.",
                        now, List.of(budget.id()), Math.max(0, average - limit)));
            } else if (latest > limit) {
                out.add(build(userId, RecommendationType.BUDGET_OVERRUN, subject,
                        "Budget " + budget.categoryName() + " dépassé",
                        "Vos dépenses dépassent ce budget de " + InsightText.euros(latest - limit) + " sur le dernier mois observé.",
                        Confidence.MEDIUM, snapshot, detail, null, "Surveiller ce poste le mois prochain.",
                        now, List.of(budget.id()), latest - limit));
            } else if (fullWindow && spent.stream().allMatch(value -> InsightText.percent(value, limit) < properties.budgetLowUsagePercent())) {
                out.add(build(userId, RecommendationType.BUDGET_LIMIT_HIGH, subject,
                        "Budget " + budget.categoryName() + " peut-être trop large",
                        "Vous utilisez moins de " + properties.budgetLowUsagePercent() + " % de ce budget depuis " + spent.size() + " mois.",
                        Confidence.MEDIUM, snapshot, detail, null, "Vous pouvez ajuster cette limite si vous le souhaitez.",
                        now, List.of(budget.id()), 0));
            } else if (fullWindow && spent.get(0) > spent.get(1) && spent.get(1) > spent.get(2)
                    && InsightText.percent(latest, limit) >= properties.budgetTightUsagePercent()) {
                out.add(build(userId, RecommendationType.BUDGET_MARGIN_SHRINKING, subject,
                        "La marge du budget " + budget.categoryName() + " diminue",
                        "Il reste " + InsightText.euros(limit - latest) + " de marge, en baisse depuis " + spent.size() + " mois.",
                        Confidence.MEDIUM, snapshot, detail, null, "Surveiller ce poste avant de dépasser la limite.",
                        now, List.of(budget.id()), limit - latest));
            }
        }
    }

    private void recurring(UUID userId, SpendingSnapshot snapshot, List<Series> series, Instant now, List<Scored> out) {
        Map<UUID, List<Row>> rows = snapshot.rows().stream()
                .filter(row -> row.flow() == Flow.EXPENSE && row.seriesId() != null)
                .collect(Collectors.groupingBy(Row::seriesId));
        Set<UUID> known = series.stream().map(Series::id).collect(Collectors.toSet());
        for (Series item : series) {
            List<Row> items = rows.getOrDefault(item.id(), List.of());
            List<Row> past = items.stream().filter(row -> YearMonth.from(row.date()).isBefore(snapshot.month())).toList();
            List<Row> current = items.stream().filter(row -> YearMonth.from(row.date()).equals(snapshot.month())).toList();
            if (past.size() < properties.minHistoryMonths() || current.isEmpty()) continue;
            long median = AnomalyDetector.median(past.stream().mapToLong(Row::absolute).toArray());
            long latest = current.get(current.size() - 1).absolute();
            long delta = latest - median;
            if (median == 0 || delta < properties.minPriceIncreaseMinor()
                    || InsightText.percent(delta, median) < properties.priceIncreasePercent()) continue;
            String name = item.subscriptionName() != null ? item.subscriptionName() : item.label();
            out.add(build(userId, RecommendationType.RECURRING_INCREASE, item.id().toString(),
                    "Hausse du paiement récurrent " + name,
                    "Ce paiement passe de " + InsightText.euros(median) + " à " + InsightText.euros(latest) + ".",
                    past.size() >= properties.minObservations() ? Confidence.HIGH : Confidence.MEDIUM, snapshot,
                    "Montant médian sur " + past.size() + " paiements précédents.", delta * 12,
                    "Vérifier si cette hausse est attendue.", now, current.stream().map(Row::id).toList(), delta));
        }
        long monthlyRecurring = series.stream().mapToLong(Series::monthlyEquivalentMinor).sum();
        long average = snapshot.averageExpenses();
        if (snapshot.hasComparableHistory() && average > 0 && monthlyRecurring > 0
                && InsightText.percent(monthlyRecurring, average) >= properties.recurringSharePercent()) {
            int share = InsightText.percent(monthlyRecurring, average);
            out.add(build(userId, RecommendationType.RECURRING_SHARE, "all",
                    "Paiements récurrents : " + share + " % de vos dépenses",
                    "Vos paiements récurrents représentent environ " + InsightText.euros(monthlyRecurring) + " par mois.",
                    Confidence.MEDIUM, snapshot, "Équivalent mensuel de " + series.size() + " séries, comparé à "
                            + InsightText.euros(average) + " de dépenses moyennes.", null,
                    "Passer en revue ceux qui vous servent le moins.", now, series.stream().map(Series::id).toList(), monthlyRecurring));
        }
        // Chevauchement possible : meme categorie d'abonnement, sans jamais conclure a une inutilite.
        Set<UUID> subscriptionSeries = snapshot.rows().stream()
                .filter(row -> row.seriesId() != null && known.contains(row.seriesId())
                        && SUBSCRIPTIONS_CATEGORY.equals(row.categoryName()))
                .map(Row::seriesId).collect(Collectors.toSet());
        if (subscriptionSeries.size() >= 2) {
            out.add(build(userId, RecommendationType.RECURRING_OVERLAP, "subscriptions",
                    subscriptionSeries.size() + " abonnements en parallèle",
                    "Plusieurs paiements récurrents relèvent de la catégorie Abonnements. Ils peuvent tous vous être utiles.",
                    Confidence.LOW, snapshot, "Séries récurrentes actives de la catégorie Abonnements.", null,
                    "Vérifier s'ils couvrent le même usage.", now, subscriptionSeries.stream().sorted().toList(), 0));
        }
    }

    private List<SpendingSnapshot.CategoryRef> reducible(SpendingSnapshot snapshot) {
        return snapshot.categories().stream()
                .filter(category -> properties.reducibleCategories().contains(category.name())).toList();
    }

    private static List<UUID> evidence(SpendingSnapshot snapshot, UUID categoryId) {
        return snapshot.expenses(snapshot.month()).stream()
                .filter(row -> categoryId.equals(row.categoryId())).map(Row::id).toList();
    }

    private static long wholeEuros(long minor) {
        return minor / 100 * 100;
    }

    /** Score explicable : solidite des donnees, puis impact, puis urgence du type. */
    private static Scored build(UUID userId, RecommendationType type, String subject, String title, String description,
                                Confidence confidence, SpendingSnapshot snapshot, String justification, Long annual,
                                String action, Instant now, List<UUID> evidence, long impactMinor) {
        int confidencePoints = switch (confidence) {
            case HIGH -> 40;
            case MEDIUM -> 25;
            case LOW -> 10;
        };
        int impactPoints = (int) Math.min(40, impactMinor / 250);
        int typePoints = switch (type) {
            case POST_CANCELLATION_CHARGE -> 40;
            case BUDGET_OVERRUN, RECURRING_INCREASE, CANCELLATION_UNFINISHED -> 20;
            case REDUCE_SPENDING, SUBSCRIPTION_RENEWAL -> 15;
            case BUDGET_MARGIN_SHRINKING -> 10;
            case SAVINGS_POTENTIAL, RECURRING_SHARE, SUBSCRIPTION_SAVINGS -> 5;
            case BUDGET_LIMIT_HIGH, RECURRING_OVERLAP -> 0;
        };
        int score = confidencePoints + impactPoints + typePoints;
        Priority priority = score >= 70 ? Priority.HIGH : score >= 40 ? Priority.MEDIUM : Priority.LOW;
        UUID id = UUID.nameUUIDFromBytes((userId + "|" + type + "|" + subject).getBytes(StandardCharsets.UTF_8));
        return new Scored(new RecommendationResponse(id, type, title, description, priority, confidence,
                snapshot.month().toString(), justification, annual, action, now, evidence, RecommendationStatus.NEW), score);
    }
}
