package com.eparly.api.insights.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.insights.config.InsightsProperties;
import com.eparly.api.insights.domain.InsightModels.Flow;
import com.eparly.api.insights.domain.InsightModels.MonthCoverage;
import com.eparly.api.insights.domain.InsightModels.RecommendationStatus;
import com.eparly.api.insights.domain.InsightModels.Row;
import com.eparly.api.insights.domain.InsightModels.Trend;
import com.eparly.api.insights.dto.InsightDtos;
import com.eparly.api.insights.dto.InsightDtos.AnomaliesResponse;
import com.eparly.api.insights.dto.InsightDtos.CategorySpend;
import com.eparly.api.insights.dto.InsightDtos.CategoryTrend;
import com.eparly.api.insights.dto.InsightDtos.DataQuality;
import com.eparly.api.insights.dto.InsightDtos.FixedVariable;
import com.eparly.api.insights.dto.InsightDtos.FrequentMerchant;
import com.eparly.api.insights.dto.InsightDtos.MonthPoint;
import com.eparly.api.insights.dto.InsightDtos.OverviewResponse;
import com.eparly.api.insights.dto.InsightDtos.RecommendationResponse;
import com.eparly.api.insights.dto.InsightDtos.RecommendationsResponse;
import com.eparly.api.insights.dto.InsightDtos.SavingsSummary;
import com.eparly.api.insights.dto.InsightDtos.TopExpense;
import com.eparly.api.insights.dto.InsightDtos.TrendsResponse;
import com.eparly.api.insights.dto.InsightDtos.WeekdayConcentration;
import com.eparly.api.insights.repository.InsightsRepository;
import com.eparly.api.savings.action.dto.SavingsPlanSummaryResponse;
import com.eparly.api.savings.action.service.SavingsActionService;
import com.eparly.api.subscription.service.SubscriptionAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcul a la demande sur une fenetre bornee, sans cache : un import ou une correction de categorie est donc pris en
 * compte des la requete suivante. Seul le choix de l'utilisateur sur une recommandation est persiste.
 */
@Service
public class InsightsService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final int TOP_EXPENSES = 5;

    private final InsightsRepository repository;
    private final InsightsProperties properties;
    private final AccountRepository accountRepository;
    private final AnomalyDetector anomalyDetector;
    private final RecommendationEngine recommendationEngine;
    private final SavingsActionService savingsActionService;
    private final CommercialAlternativesService alternatives;
    private final SubscriptionAnalysisService subscriptionAnalysis;
    private final Clock clock;

    public InsightsService(InsightsRepository repository, InsightsProperties properties, AccountRepository accountRepository,
                           AnomalyDetector anomalyDetector, RecommendationEngine recommendationEngine,
                           SavingsActionService savingsActionService, CommercialAlternativesService alternatives,
                           SubscriptionAnalysisService subscriptionAnalysis, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.accountRepository = accountRepository;
        this.anomalyDetector = anomalyDetector;
        this.recommendationEngine = recommendationEngine;
        this.savingsActionService = savingsActionService;
        this.alternatives = alternatives;
        this.subscriptionAnalysis = subscriptionAnalysis;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(UUID userId, YearMonth requested, UUID accountId) {
        SpendingSnapshot snapshot = snapshot(userId, requested, accountId);
        YearMonth month = snapshot.month();
        YearMonth previous = month.minusMonths(1);
        long expenses = snapshot.total(month, Flow.EXPENSE);
        boolean monthComplete = snapshot.coverage(month) == MonthCoverage.COMPLETE;
        boolean previousComplete = snapshot.coverage(previous) == MonthCoverage.COMPLETE;
        long previousExpenses = snapshot.total(previous, Flow.EXPENSE);
        Integer change = previousComplete && monthComplete && previousExpenses > 0
                ? InsightText.percent(expenses - previousExpenses, previousExpenses) : null;
        long income = snapshot.total(month, Flow.INCOME);
        long refunds = snapshot.total(month, Flow.REFUND);
        Long balance = income > 0 && monthComplete ? income + refunds - expenses : null;
        List<Row> rows = snapshot.expenses(month);
        long fixed = rows.stream().filter(row -> row.seriesId() != null).mapToLong(Row::absolute).sum();
        List<CategorySpend> categories = rows.stream()
                .collect(Collectors.groupingBy(row -> new SpendingSnapshot.CategoryRef(row.categoryId(), row.categoryName())))
                .entrySet().stream().map(entry -> {
                    long amount = entry.getValue().stream().mapToLong(Row::absolute).sum();
                    return new CategorySpend(entry.getKey().id(), entry.getKey().name(), amount, entry.getValue().size(),
                            InsightText.percent(amount, expenses));
                })
                .sorted(Comparator.comparingLong(CategorySpend::amountMinor).reversed()
                        .thenComparing((CategorySpend item) -> String.valueOf(item.categoryName())))
                .toList();
        List<TopExpense> top = rows.stream()
                .sorted(Comparator.comparingLong(Row::absolute).reversed().thenComparing(Row::id))
                .limit(TOP_EXPENSES)
                .map(row -> new TopExpense(row.id(), row.date(), row.label(), row.categoryName(), row.absolute())).toList();
        boolean comparable = snapshot.hasComparableHistory();
        return new OverviewResponse(month.toString(), expenses, previous.toString(), previousComplete ? previousExpenses : null,
                change, comparable ? snapshot.averageExpenses() : null, comparable ? snapshot.baseline().size() : 0,
                refunds, income, balance, new FixedVariable(fixed, expenses - fixed), categories, top, quality(snapshot));
    }

    @Transactional(readOnly = true)
    public TrendsResponse trends(UUID userId, YearMonth requested, UUID accountId) {
        SpendingSnapshot snapshot = snapshot(userId, requested, accountId);
        List<MonthPoint> months = snapshot.window().stream().map(month -> new MonthPoint(month.toString(),
                snapshot.coverage(month), snapshot.total(month, Flow.EXPENSE), snapshot.total(month, Flow.REFUND),
                snapshot.total(month, Flow.INCOME), snapshot.rowsIn(month).size())).toList();
        List<YearMonth> complete = new ArrayList<>(snapshot.baseline());
        if (snapshot.coverage(snapshot.month()) == MonthCoverage.COMPLETE) complete.add(snapshot.month());
        List<CategoryTrend> categories = snapshot.categories().stream()
                .map(category -> categoryTrend(snapshot, complete, category)).toList();
        return new TrendsResponse(snapshot.month().toString(), months, categories, frequentMerchants(snapshot, complete),
                weekdayConcentration(snapshot, complete), quality(snapshot));
    }

    /** Tendance : les derniers mois complets evoluent toujours dans le meme sens, avec un ecart significatif. */
    private CategoryTrend categoryTrend(SpendingSnapshot snapshot, List<YearMonth> complete, SpendingSnapshot.CategoryRef category) {
        boolean nonEssential = properties.reducibleCategories().contains(category.name());
        int count = properties.minHistoryMonths();
        if (complete.size() < count) {
            return new CategoryTrend(category.id(), category.name(), Trend.INSUFFICIENT_HISTORY, null, nonEssential, List.of(), null);
        }
        List<Long> values = complete.subList(complete.size() - count, complete.size()).stream()
                .map(month -> snapshot.categoryTotal(month, category.id())).toList();
        long first = values.get(0);
        long last = values.get(count - 1);
        boolean rising = true;
        boolean falling = true;
        for (int index = 1; index < count; index++) {
            rising &= values.get(index) > values.get(index - 1);
            falling &= values.get(index) < values.get(index - 1);
        }
        Integer change = first == 0 ? null : InsightText.percent(last - first, first);
        boolean significant = Math.abs(last - first) >= properties.minChangeMinor()
                && (change == null || Math.abs(change) >= properties.minChangePercent());
        Trend trend = significant && rising ? Trend.RISING : significant && falling ? Trend.FALLING : Trend.STABLE;
        long average = Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
        return new CategoryTrend(category.id(), category.name(), trend, change, nonEssential, values, average);
    }

    private List<FrequentMerchant> frequentMerchants(SpendingSnapshot snapshot, List<YearMonth> complete) {
        if (complete.size() < properties.minHistoryMonths()) return List.of();
        List<Row> rows = complete.stream().flatMap(month -> snapshot.expenses(month).stream()).toList();
        return rows.stream().collect(Collectors.groupingBy(Row::label)).entrySet().stream()
                .filter(entry -> entry.getValue().size() >= properties.frequentMerchantPerMonth() * complete.size())
                .map(entry -> new FrequentMerchant(entry.getKey(),
                        Math.round(entry.getValue().size() * 10.0 / complete.size()) / 10.0,
                        entry.getValue().stream().mapToLong(Row::absolute).sum(), entry.getValue().size()))
                .sorted(Comparator.comparingLong(FrequentMerchant::totalMinor).reversed().thenComparing(FrequentMerchant::label))
                .toList();
    }

    private WeekdayConcentration weekdayConcentration(SpendingSnapshot snapshot, List<YearMonth> complete) {
        List<Row> rows = complete.stream().flatMap(month -> snapshot.expenses(month).stream()).toList();
        if (rows.size() < properties.weekdayMinTransactions()) return null;
        Map<DayOfWeek, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(row -> row.date().getDayOfWeek(), Collectors.counting()));
        Map.Entry<DayOfWeek, Long> top = counts.entrySet().stream()
                .max(Map.Entry.<DayOfWeek, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey())).orElseThrow();
        int share = InsightText.percent(top.getValue(), rows.size());
        return share >= properties.weekdayConcentrationPercent()
                ? new WeekdayConcentration(top.getKey().name(), share, top.getValue()) : null;
    }

    @Transactional(readOnly = true)
    public AnomaliesResponse anomalies(UUID userId, YearMonth requested, UUID accountId, int page, int size) {
        SpendingSnapshot snapshot = snapshot(userId, requested, accountId);
        return new AnomaliesResponse(page(anomalyDetector.detect(userId, snapshot), page, size), quality(snapshot));
    }

    @Transactional(readOnly = true)
    public RecommendationsResponse recommendations(UUID userId, Jwt jwt, YearMonth requested, int page, int size) {
        SpendingSnapshot snapshot = snapshot(userId, requested, null);
        Instant now = clock.instant();
        Map<UUID, InsightsRepository.RecommendationState> states = repository.states(userId);
        List<RecommendationResponse> visible = new ArrayList<>();
        for (RecommendationResponse item : computeAll(userId, snapshot, now)) {
            InsightsRepository.RecommendationState state = states.get(item.id());
            if (state == null) {
                visible.add(item);
            } else if (state.hiddenUntil() == null) {
                visible.add(withStatus(item, state.status()));
            } else if (!state.hiddenUntil().isAfter(now)) {
                // Le delai est ecoule et la situation persiste : elle redevient une nouveaute.
                visible.add(item);
            }
        }
        long potential = visible.stream().filter(item -> item.potentialAnnualSavingsMinor() != null)
                .mapToLong(RecommendationResponse::potentialAnnualSavingsMinor).sum();
        SavingsPlanSummaryResponse plan = savingsActionService.summary(jwt);
        return new RecommendationsResponse(page(visible, page, size),
                new SavingsSummary(potential, plan.potentialSavingsMinor(), plan.observedSavingsMinor()),
                alternatives.current(), quality(snapshot));
    }

    /** Ne vaut que pour une recommandation calculee ou deja suivie par cet utilisateur : sinon 404 neutre. */
    @Transactional
    public void updateStatus(UUID userId, UUID recommendationId, RecommendationStatus status, YearMonth requested) {
        SpendingSnapshot snapshot = snapshot(userId, requested, null);
        Instant now = clock.instant();
        boolean known = repository.states(userId).containsKey(recommendationId)
                || computeAll(userId, snapshot, now).stream().anyMatch(item -> item.id().equals(recommendationId));
        if (!known) throw new ApiException(HttpStatus.NOT_FOUND, "Recommendation not found");
        boolean hide = status == RecommendationStatus.IGNORED || status == RecommendationStatus.DONE;
        repository.saveState(userId, recommendationId, status, hide ? now.plus(properties.hideDuration()) : null);
    }

    private List<RecommendationResponse> computeAll(UUID userId, SpendingSnapshot snapshot, Instant now) {
        return recommendationEngine.compute(userId, snapshot, repository.budgets(userId), repository.activeSeries(userId),
                subscriptionAnalysis.signals(userId), now);
    }

    private SpendingSnapshot snapshot(UUID userId, YearMonth requested, UUID accountId) {
        if (accountId != null && !accountRepository.existsByIdAndUserId(accountId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        YearMonth month = requested == null ? YearMonth.from(today) : requested;
        if (month.isAfter(YearMonth.from(today))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "month must not be in the future");
        }
        LocalDate from = month.minusMonths(properties.lookbackMonths()).atDay(1);
        return new SpendingSnapshot(repository.transactions(userId, from, month.atEndOfMonth(), accountId), month,
                properties.lookbackMonths(), today, properties.partialMonthToleranceDays(), properties.minHistoryMonths());
    }

    private static DataQuality quality(SpendingSnapshot snapshot) {
        List<Row> rows = snapshot.rowsIn(snapshot.month());
        return new DataQuality(snapshot.sufficiency(), snapshot.coverage(snapshot.month()), snapshot.baseline().size(),
                snapshot.firstDate(), snapshot.lastDate(),
                rows.stream().filter(row -> row.flow() == Flow.EXPENSE && row.categoryId() == null).count(),
                rows.stream().filter(row -> row.flow() == Flow.UNKNOWN).count(),
                rows.stream().filter(row -> row.flow() == Flow.UNKNOWN && row.amountMinor() < 0).mapToLong(Row::absolute).sum(),
                rows.stream().filter(row -> row.flow() == Flow.UNKNOWN && row.amountMinor() > 0).mapToLong(Row::absolute).sum(),
                rows.stream().filter(row -> row.flow() == Flow.TRANSFER).count(),
                snapshot.excludedCurrencies(), true, InsightDtos.METHOD);
    }

    private static RecommendationResponse withStatus(RecommendationResponse item, RecommendationStatus status) {
        return new RecommendationResponse(item.id(), item.type(), item.title(), item.description(), item.priority(),
                item.confidence(), item.analysisPeriod(), item.justification(), item.potentialAnnualSavingsMinor(),
                item.suggestedAction(), item.computedAt(), item.evidenceIds(), status);
    }

    private static <T> PageResponse<T> page(List<T> items, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int from = (int) Math.min((long) safePage * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        int pages = (int) Math.ceil(items.size() / (double) safeSize);
        return new PageResponse<>(items.subList(from, to), safePage, safeSize, items.size(), pages);
    }
}
