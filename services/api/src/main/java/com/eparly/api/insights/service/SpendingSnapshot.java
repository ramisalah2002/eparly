package com.eparly.api.insights.service;

import com.eparly.api.insights.domain.InsightModels.DataSufficiency;
import com.eparly.api.insights.domain.InsightModels.Flow;
import com.eparly.api.insights.domain.InsightModels.MonthCoverage;
import com.eparly.api.insights.domain.InsightModels.Row;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactions d'une fenetre d'analyse, avec la couverture de chaque mois. Un mois sans transaction n'est pas un mois
 * a zero : les donnees manquent peut-etre. Il est donc exclu des moyennes. Seul l'EUR est agrege.
 */
public final class SpendingSnapshot {
    private final YearMonth month;
    private final LocalDate today;
    private final int partialToleranceDays;
    private final int minHistoryMonths;
    private final List<Row> eur;
    private final Map<String, Long> excludedCurrencies;
    private final LocalDate firstDate;
    private final LocalDate lastDate;
    private final List<YearMonth> window;

    public SpendingSnapshot(List<Row> rows, YearMonth month, int lookbackMonths, LocalDate today,
                            int partialToleranceDays, int minHistoryMonths) {
        this.month = month;
        this.today = today;
        this.partialToleranceDays = partialToleranceDays;
        this.minHistoryMonths = minHistoryMonths;
        this.eur = rows.stream().filter(row -> "EUR".equals(row.currency())).toList();
        this.excludedCurrencies = rows.stream().filter(row -> !"EUR".equals(row.currency()))
                .collect(Collectors.groupingBy(Row::currency, TreeMap::new, Collectors.counting()));
        this.firstDate = eur.stream().map(Row::date).min(Comparator.naturalOrder()).orElse(null);
        this.lastDate = eur.stream().map(Row::date).max(Comparator.naturalOrder()).orElse(null);
        List<YearMonth> months = new ArrayList<>();
        for (int back = lookbackMonths; back >= 0; back--) months.add(month.minusMonths(back));
        this.window = List.copyOf(months);
    }

    public YearMonth month() {
        return month;
    }

    public List<YearMonth> window() {
        return window;
    }

    public List<Row> rows() {
        return eur;
    }

    public Map<String, Long> excludedCurrencies() {
        return excludedCurrencies;
    }

    public LocalDate firstDate() {
        return firstDate;
    }

    public LocalDate lastDate() {
        return lastDate;
    }

    public List<Row> rowsIn(YearMonth target) {
        return eur.stream().filter(row -> YearMonth.from(row.date()).equals(target)).toList();
    }

    public List<Row> expenses(YearMonth target) {
        return rowsIn(target).stream().filter(row -> row.flow() == Flow.EXPENSE).toList();
    }

    public long total(YearMonth target, Flow flow) {
        return rowsIn(target).stream().filter(row -> row.flow() == flow).mapToLong(Row::absolute).sum();
    }

    public long categoryTotal(YearMonth target, UUID categoryId) {
        return expenses(target).stream().filter(row -> categoryId.equals(row.categoryId())).mapToLong(Row::absolute).sum();
    }

    /** Le premier et le dernier mois observes sont partiels si les donnees s'arretent loin de leurs bornes. */
    public MonthCoverage coverage(YearMonth target) {
        if (rowsIn(target).isEmpty()) return MonthCoverage.NO_DATA;
        if (target.equals(YearMonth.from(today))) return MonthCoverage.PARTIAL;
        if (target.equals(YearMonth.from(firstDate)) && firstDate.getDayOfMonth() > 1 + partialToleranceDays) {
            return MonthCoverage.PARTIAL;
        }
        if (target.equals(YearMonth.from(lastDate))
                && lastDate.getDayOfMonth() < target.lengthOfMonth() - partialToleranceDays) {
            return MonthCoverage.PARTIAL;
        }
        return MonthCoverage.COMPLETE;
    }

    /** Mois complets anterieurs au mois analyse : la seule base de comparaison. */
    public List<YearMonth> baseline() {
        return window.stream().filter(candidate -> candidate.isBefore(month))
                .filter(candidate -> coverage(candidate) == MonthCoverage.COMPLETE).toList();
    }

    public boolean hasComparableHistory() {
        return baseline().size() >= minHistoryMonths;
    }

    public DataSufficiency sufficiency() {
        if (eur.isEmpty()) return DataSufficiency.NO_DATA;
        return hasComparableHistory() ? DataSufficiency.SUFFICIENT : DataSufficiency.LIMITED_HISTORY;
    }

    public long averageExpenses() {
        return Math.round(baseline().stream().mapToLong(m -> total(m, Flow.EXPENSE)).average().orElse(0));
    }

    public long averageForCategory(UUID categoryId) {
        return Math.round(baseline().stream().mapToLong(m -> categoryTotal(m, categoryId)).average().orElse(0));
    }

    public record CategoryRef(UUID id, String name) {
    }

    /** Categories ayant au moins une depense dans la fenetre, triees par nom pour un resultat stable. */
    public List<CategoryRef> categories() {
        return eur.stream().filter(row -> row.flow() == Flow.EXPENSE && row.categoryId() != null)
                .map(row -> new CategoryRef(row.categoryId(), row.categoryName())).distinct()
                .sorted(Comparator.comparing(CategoryRef::name)).toList();
    }
}
