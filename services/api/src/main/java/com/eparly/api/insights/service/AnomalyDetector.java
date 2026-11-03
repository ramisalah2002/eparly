package com.eparly.api.insights.service;

import com.eparly.api.insights.config.InsightsProperties;
import com.eparly.api.insights.domain.InsightModels.AnomalyType;
import com.eparly.api.insights.domain.InsightModels.Confidence;
import com.eparly.api.insights.domain.InsightModels.Flow;
import com.eparly.api.insights.domain.InsightModels.Priority;
import com.eparly.api.insights.domain.InsightModels.Row;
import com.eparly.api.insights.dto.InsightDtos.AnomalyResponse;
import com.eparly.api.insights.dto.InsightDtos.HistoryBasis;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regles deterministes. Le vocabulaire reste celui du signalement : rien n'est jamais declare frauduleux.
 * Les references sont robustes (mediane et ecart absolu median), donc peu sensibles a un achat isole.
 */
@Component
public class AnomalyDetector {
    private static final long HIGH_SEVERITY_MINOR = 10_000;
    private static final long MEDIUM_SEVERITY_MINOR = 3_000;
    private static final int STABLE_SPREAD_PERCENT = 10;

    private final InsightsProperties properties;

    public AnomalyDetector(InsightsProperties properties) {
        this.properties = properties;
    }

    public List<AnomalyResponse> detect(UUID userId, SpendingSnapshot snapshot) {
        List<AnomalyResponse> anomalies = new ArrayList<>();
        unusualAmounts(userId, snapshot, anomalies);
        categorySpikes(userId, snapshot, anomalies);
        duplicates(userId, snapshot, anomalies);
        merchantIncreases(userId, snapshot, anomalies);
        recurringVariations(userId, snapshot, anomalies);
        anomalies.sort(Comparator.comparing(AnomalyResponse::severity).reversed()
                .thenComparing(Comparator.comparingLong(AnomalyResponse::amountMinor).reversed())
                .thenComparing(AnomalyResponse::id));
        return anomalies;
    }

    private List<Row> history(SpendingSnapshot snapshot) {
        return snapshot.baseline().stream().flatMap(month -> snapshot.expenses(month).stream()).toList();
    }

    private void unusualAmounts(UUID userId, SpendingSnapshot snapshot, List<AnomalyResponse> out) {
        // Un abonnement a sa propre regle : seuls les achats hors serie sont compares ici.
        Map<UUID, List<Row>> byCategory = history(snapshot).stream()
                .filter(row -> row.seriesId() == null && row.categoryId() != null)
                .collect(Collectors.groupingBy(Row::categoryId));
        for (Row row : snapshot.expenses(snapshot.month())) {
            if (row.seriesId() != null || row.categoryId() == null) continue;
            List<Row> past = byCategory.getOrDefault(row.categoryId(), List.of());
            if (past.size() < properties.minObservations()) continue;
            long[] amounts = past.stream().mapToLong(Row::absolute).toArray();
            long median = median(amounts);
            long threshold = Math.max(median * properties.unusualMedianMultiplier(),
                    median + Math.round(properties.unusualMadMultiplier() * 1.4826 * mad(amounts, median)));
            if (row.absolute() < properties.minUnusualAmountMinor() || row.absolute() <= threshold) continue;
            out.add(anomaly(userId, AnomalyType.UNUSUAL_AMOUNT, row.id().toString(), row.absolute(),
                    new HistoryBasis(past.size(), median, "Depense mediane de la categorie sur les mois complets"),
                    "Cette depense de " + InsightText.euros(row.absolute()) + " en " + row.categoryName()
                            + " est nettement superieure a votre montant habituel de " + InsightText.euros(median) + ".",
                    past.size() >= properties.minObservations() * 2 ? Confidence.HIGH : Confidence.MEDIUM,
                    List.of(row.id()), snapshot));
        }
    }

    private void categorySpikes(UUID userId, SpendingSnapshot snapshot, List<AnomalyResponse> out) {
        if (!snapshot.hasComparableHistory()) return;
        for (SpendingSnapshot.CategoryRef category : snapshot.categories()) {
            long total = snapshot.categoryTotal(snapshot.month(), category.id());
            long average = snapshot.averageForCategory(category.id());
            long excess = total - average;
            if (average == 0 || excess < properties.minChangeMinor()
                    || InsightText.percent(excess, average) < properties.minChangePercent()) continue;
            List<UUID> evidence = snapshot.expenses(snapshot.month()).stream()
                    .filter(row -> category.id().equals(row.categoryId())).map(Row::id).toList();
            out.add(anomaly(userId, AnomalyType.CATEGORY_SPIKE, category.id().toString(), excess,
                    new HistoryBasis(snapshot.baseline().size(), average, "Moyenne mensuelle de la categorie sur les mois complets"),
                    "Vos depenses " + category.name() + " atteignent " + InsightText.euros(total) + ", soit "
                            + InsightText.euros(excess) + " de plus que votre moyenne de " + InsightText.euros(average) + ".",
                    snapshot.baseline().size() >= properties.minHistoryMonths() * 2 ? Confidence.HIGH : Confidence.MEDIUM,
                    evidence, snapshot));
        }
    }

    private void duplicates(UUID userId, SpendingSnapshot snapshot, List<AnomalyResponse> out) {
        List<Row> expenses = snapshot.rows().stream().filter(row -> row.flow() == Flow.EXPENSE && row.seriesId() == null
                && row.absolute() >= properties.minDuplicateAmountMinor()).toList();
        Set<UUID> reported = new HashSet<>();
        Map<String, List<Row>> groups = expenses.stream()
                .collect(Collectors.groupingBy(row -> row.label() + "|" + row.amountMinor()));
        for (List<Row> group : groups.values()) {
            List<Row> rows = group.stream().sorted(Comparator.comparing(Row::date).thenComparing(Row::id)).toList();
            for (int index = 1; index < rows.size(); index++) {
                Row first = rows.get(index - 1);
                Row second = rows.get(index);
                boolean close = ChronoUnit.DAYS.between(first.date(), second.date()) <= properties.duplicateWindowDays();
                if (!close || !YearMonth.from(second.date()).equals(snapshot.month()) || !reported.add(second.id())) continue;
                out.add(anomaly(userId, AnomalyType.POSSIBLE_DUPLICATE, first.id() + "|" + second.id(), second.absolute(),
                        new HistoryBasis(2, second.absolute(), "Meme libelle et meme montant a dates proches"),
                        "Deux operations de " + InsightText.euros(second.absolute()) + " portent le meme libelle a dates proches. "
                                + "Il peut s'agir de deux achats reels : a verifier sur votre releve.",
                        Confidence.MEDIUM, List.of(first.id(), second.id()), snapshot));
            }
        }
    }

    private void merchantIncreases(UUID userId, SpendingSnapshot snapshot, List<AnomalyResponse> out) {
        Map<String, List<Row>> byLabel = history(snapshot).stream().filter(row -> row.seriesId() == null)
                .collect(Collectors.groupingBy(Row::label));
        for (Row row : snapshot.expenses(snapshot.month())) {
            List<Row> past = byLabel.getOrDefault(row.label(), List.of());
            if (row.seriesId() != null || past.size() < properties.minObservations()) continue;
            long[] amounts = past.stream().mapToLong(Row::absolute).toArray();
            long median = median(amounts);
            long delta = row.absolute() - median;
            long spread = Arrays.stream(amounts).max().orElse(0) - Arrays.stream(amounts).min().orElse(0);
            // Seul un commercant au montant stable a un prix "habituel" : sinon la hausse ne signifie rien.
            if (median == 0 || InsightText.percent(spread, median) > STABLE_SPREAD_PERCENT
                    || delta < properties.minPriceIncreaseMinor()
                    || InsightText.percent(delta, median) < properties.priceIncreasePercent()) continue;
            out.add(anomaly(userId, AnomalyType.MERCHANT_AMOUNT_INCREASE, row.id().toString(), delta,
                    new HistoryBasis(past.size(), median, "Montant median chez ce commercant"),
                    "Le montant chez " + row.label() + " passe de " + InsightText.euros(median) + " a "
                            + InsightText.euros(row.absolute()) + ".",
                    Confidence.MEDIUM, List.of(row.id()), snapshot));
        }
    }

    private void recurringVariations(UUID userId, SpendingSnapshot snapshot, List<AnomalyResponse> out) {
        Map<UUID, List<Row>> bySeries = snapshot.rows().stream()
                .filter(row -> row.flow() == Flow.EXPENSE && row.seriesId() != null
                        && YearMonth.from(row.date()).isBefore(snapshot.month()))
                .collect(Collectors.groupingBy(Row::seriesId));
        for (Row row : snapshot.expenses(snapshot.month())) {
            List<Row> past = row.seriesId() == null ? List.of() : bySeries.getOrDefault(row.seriesId(), List.of());
            if (past.size() < properties.minHistoryMonths()) continue;
            long median = median(past.stream().mapToLong(Row::absolute).toArray());
            long delta = Math.abs(row.absolute() - median);
            if (median == 0 || delta < properties.minPriceIncreaseMinor()
                    || InsightText.percent(delta, median) < properties.priceIncreasePercent()) continue;
            out.add(anomaly(userId, AnomalyType.RECURRING_AMOUNT_VARIATION, row.seriesId() + "|" + row.id(), delta,
                    new HistoryBasis(past.size(), median, "Montant median de la serie recurrente"),
                    "Le paiement recurrent " + row.label() + " passe de " + InsightText.euros(median) + " a "
                            + InsightText.euros(row.absolute()) + ".",
                    past.size() >= properties.minObservations() ? Confidence.HIGH : Confidence.MEDIUM,
                    List.of(row.id()), snapshot));
        }
    }

    private static AnomalyResponse anomaly(UUID userId, AnomalyType type, String subject, long amount, HistoryBasis basis,
                                           String explanation, Confidence confidence, List<UUID> evidence,
                                           SpendingSnapshot snapshot) {
        UUID id = UUID.nameUUIDFromBytes((userId + "|" + type + "|" + subject + "|" + snapshot.month())
                .getBytes(StandardCharsets.UTF_8));
        Priority severity = amount >= HIGH_SEVERITY_MINOR ? Priority.HIGH
                : amount >= MEDIUM_SEVERITY_MINOR ? Priority.MEDIUM : Priority.LOW;
        return new AnomalyResponse(id, type, severity, amount, basis, explanation, confidence, evidence,
                snapshot.month().toString());
    }

    static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : Math.round((sorted[middle - 1] + sorted[middle]) / 2.0);
    }

    private static long mad(long[] values, long median) {
        return median(Arrays.stream(values).map(value -> Math.abs(value - median)).toArray());
    }
}
