package com.eparly.api.subscription.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.repository.RecurringSeriesRepository;
import com.eparly.api.recurring.service.LabelKey;
import com.eparly.api.subscription.config.SubscriptionTrackingProperties;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionModels;
import com.eparly.api.subscription.domain.SubscriptionModels.Cancellation;
import com.eparly.api.subscription.domain.SubscriptionModels.CancellationStatus;
import com.eparly.api.subscription.domain.SubscriptionModels.Cost;
import com.eparly.api.subscription.domain.SubscriptionModels.DateStatus;
import com.eparly.api.subscription.domain.SubscriptionModels.LedgerRow;
import com.eparly.api.subscription.domain.SubscriptionModels.VerificationState;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.CategoryCost;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.CurrencyOverview;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.DetectedSubscription;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.MonthCost;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.PossibleRedundancy;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.PriceIncrease;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.Renewal;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.ReviewItem;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionAnalysis;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionTransaction;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionTransactions;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionsOverview;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.subscription.repository.SubscriptionTrackingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lectures calculees a la demande. Les textes parlent de paiements observes, jamais de l'usage reel d'un service. */
@Service
@Transactional(readOnly = true)
public class SubscriptionAnalysisService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final int HISTORY_YEARS = 3;
    private static final int COST_MONTHS = 12;
    private static final Map<String, Set<String>> FAMILIES = Map.of(
            "VIDEO", Set.of("NETFLIX", "DISNEY", "PRIME VIDEO", "CANAL", "HBO", "PARAMOUNT"),
            "MUSIC", Set.of("SPOTIFY", "DEEZER", "APPLE MUSIC", "YOUTUBE MUSIC", "TIDAL"),
            "CLOUD", Set.of("ICLOUD", "GOOGLE ONE", "DROPBOX", "ONEDRIVE"));

    private final SubscriptionRepository subscriptions;
    private final SubscriptionTrackingRepository repository;
    private final SubscriptionLedgerService ledger;
    private final CancellationService cancellations;
    private final RecurringSeriesRepository seriesRepository;
    private final SubscriptionTrackingProperties properties;
    private final Clock clock;

    public SubscriptionAnalysisService(SubscriptionRepository subscriptions, SubscriptionTrackingRepository repository,
                                       SubscriptionLedgerService ledger, CancellationService cancellations,
                                       RecurringSeriesRepository seriesRepository, SubscriptionTrackingProperties properties, Clock clock) {
        this.subscriptions = subscriptions;
        this.repository = repository;
        this.ledger = ledger;
        this.cancellations = cancellations;
        this.seriesRepository = seriesRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Signal transmis au moteur de recommandations existant, qui le classe et gere son suivi. */
    public record Signal(String type, String subject, String title, String description, boolean strong, String justification,
                         Long annualMinor, String action, List<UUID> evidence, long impactMinor) {
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private List<Subscription> tracked(UUID userId) {
        return subscriptions.findAllByUserIdFiltered(userId, null, null).stream()
                .filter(item -> item.getArchivedAt() == null).toList();
    }

    private List<LedgerRow> userRows(UUID userId) {
        return repository.expenseRows(userId, today().minusYears(HISTORY_YEARS));
    }

    /** Series detectees non confirmees : une detection reste une proposition tant que l'utilisateur ne l'a pas validee. */
    public List<DetectedSubscription> detected(UUID userId) {
        List<Subscription> existing = tracked(userId);
        return seriesRepository.findAllByUserIdFiltered(userId, null, RecurringSeriesStatus.CANDIDATE, null).stream()
                .filter(series -> !repository.seriesAlreadyLinked(series.getId()))
                .map(series -> new DetectedSubscription(series.getId(), series.getAccount().getId(), series.getNormalizedLabel(),
                        series.getCurrency(), series.getFrequency(), series.getUsualAmountMinor(), series.getOccurrenceCount(),
                        series.getLastOccurrenceDate(), series.getNextEstimatedDate(), series.getConfidenceScore(),
                        possibleMatch(series, existing)))
                .toList();
    }

    private UUID possibleMatch(RecurringSeries series, List<Subscription> existing) {
        String key = " " + LabelKey.of(series.getNormalizedLabel()) + " ";
        return existing.stream().filter(item -> matches(key, item) && item.getCurrency().equals(series.getCurrency()))
                .map(Subscription::getId).findFirst().orElse(null);
    }

    private boolean matches(String key, Subscription item) {
        List<RecurringSeries> linked = ledger.series(item);
        if (!linked.isEmpty()) return linked.stream().anyMatch(other -> key.equals(" " + LabelKey.of(other.getNormalizedLabel()) + " "));
        String name = LabelKey.of(item.getName());
        return name.length() >= 4 && key.contains(" " + name + " ");
    }

    public SubscriptionTransactions transactions(UUID userId, UUID id) {
        Subscription subscription = subscriptions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));
        List<SubscriptionTransaction> rows = ledger.rows(subscription, userRows(userId)).stream()
                .sorted(Comparator.comparing(LedgerRow::date).reversed())
                .map(row -> new SubscriptionTransaction(row.id(), row.accountId(), row.date(), row.amountMinor(), row.currency(), row.label(), row.categoryName()))
                .toList();
        return new SubscriptionTransactions(ledger.series(subscription).isEmpty() ? "NAME" : "SERIES", rows);
    }

    public List<Renewal> renewals(UUID userId, int days) {
        LocalDate today = today();
        LocalDate horizon = today.plusDays(Math.max(0, Math.min(days, 366)));
        List<Renewal> renewals = new ArrayList<>();
        for (Subscription item : tracked(userId)) {
            if (item.getDeclaredStatus() == SubscriptionStatus.CANCELED) continue;
            Cost cost = ledger.cost(item);
            LocalDate date = nextDate(item, cost, today);
            if (date != null && !date.isAfter(horizon)) {
                boolean confirmed = date.equals(item.getNextDueDate());
                renewals.add(new Renewal(item.getId(), item.getName(), "RENEWAL", date,
                        confirmed ? DateStatus.CONFIRMED : DateStatus.ESTIMATED, cost.amountMinor(), cost.currency()));
            }
            LocalDate commitment = item.getCommitmentEndDate();
            if (commitment != null && !commitment.isBefore(today) && !commitment.isAfter(horizon)) {
                renewals.add(new Renewal(item.getId(), item.getName(), "COMMITMENT_END", commitment, DateStatus.CONFIRMED, cost.amountMinor(), cost.currency()));
            }
        }
        renewals.sort(Comparator.comparing(Renewal::date).thenComparing(Renewal::subscriptionId));
        return renewals;
    }

    /** Une date saisie et encore a venir prime ; sinon on projette la derniere estimation, en la signalant comme telle. */
    private LocalDate nextDate(Subscription item, Cost cost, LocalDate today) {
        if (item.getNextDueDate() != null && !item.getNextDueDate().isBefore(today)) return item.getNextDueDate();
        RecurringSeries series = item.getRecurringSeries();
        LocalDate estimate = series == null ? null : series.getNextEstimatedDate();
        if (estimate == null) estimate = item.getNextDueDate();
        if (estimate == null || cost.frequency() == RecurringFrequency.UNKNOWN) return null;
        while (estimate.isBefore(today)) estimate = SubscriptionModels.next(estimate, cost.frequency());
        return estimate;
    }

    public SubscriptionsOverview overview(UUID userId) {
        List<LedgerRow> rows = userRows(userId);
        List<Subscription> active = tracked(userId).stream().filter(item -> item.getDeclaredStatus() == SubscriptionStatus.ACTIVE).toList();
        int unknown = (int) active.stream().filter(item -> !ledger.cost(item).known()).count();
        Map<String, List<Subscription>> byCurrency = active.stream().filter(item -> ledger.cost(item).known())
                .collect(Collectors.groupingBy(Subscription::getCurrency, TreeMap::new, Collectors.toList()));
        YearMonth current = YearMonth.from(today());
        List<CurrencyOverview> currencies = new ArrayList<>();
        byCurrency.forEach((currency, items) -> {
            Map<String, Long> categories = new TreeMap<>();
            Map<YearMonth, Long> months = new LinkedHashMap<>();
            for (int back = COST_MONTHS - 1; back >= 0; back--) months.put(current.minusMonths(back), 0L);
            long monthly = 0;
            long annual = 0;
            for (Subscription item : items) {
                Cost cost = ledger.cost(item);
                monthly += cost.monthlyMinor();
                annual += cost.annualMinor();
                List<LedgerRow> own = ledger.rows(item, rows);
                String category = own.stream().filter(row -> row.categoryName() != null)
                        .collect(Collectors.groupingBy(LedgerRow::categoryName, TreeMap::new, Collectors.counting())).entrySet().stream()
                        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("Non catégorisé");
                categories.merge(category, cost.monthlyMinor(), Long::sum);
                own.forEach(row -> months.computeIfPresent(YearMonth.from(row.date()), (key, value) -> value + row.amountMinor()));
            }
            currencies.add(new CurrencyOverview(currency, items.size(), monthly, annual,
                    categories.entrySet().stream().map(entry -> new CategoryCost(entry.getKey(), entry.getValue())).toList(),
                    months.entrySet().stream().map(entry -> new MonthCost(entry.getKey().toString(), entry.getValue())).toList()));
        });
        return new SubscriptionsOverview(currencies, unknown);
    }

    public SubscriptionAnalysis analysis(UUID userId) {
        List<Subscription> items = tracked(userId).stream().filter(item -> item.getDeclaredStatus() != SubscriptionStatus.CANCELED).toList();
        List<LedgerRow> rows = userRows(userId);
        List<PriceIncrease> increases = new ArrayList<>();
        List<ReviewItem> review = new ArrayList<>();
        LocalDate today = today();
        for (Subscription item : items) {
            List<LedgerRow> own = ledger.rows(item, rows);
            PriceIncrease increase = priceIncrease(item, own);
            if (increase != null) increases.add(increase);
            Cost cost = ledger.cost(item);
            if (item.isNoLongerNeeded()) {
                review.add(new ReviewItem(item.getId(), item.getName(), "USER_MARKED_NOT_NEEDED", "Vous avez indiqué ne plus en avoir besoin."));
            }
            if (cost.known() && cost.annualMinor() >= properties.costlyAnnualMinor()) {
                review.add(new ReviewItem(item.getId(), item.getName(), "HIGH_COST", "Coût annuel estimé élevé. Eparly ne connaît pas votre usage de ce service."));
            }
            if (cost.known() && !own.isEmpty()) {
                LocalDate last = own.get(own.size() - 1).date();
                if (SubscriptionModels.next(SubscriptionModels.next(last, cost.frequency()), cost.frequency()).isBefore(today)) {
                    review.add(new ReviewItem(item.getId(), item.getName(), "PAYMENT_NOT_OBSERVED_RECENTLY",
                            "Aucun paiement observé récemment. Vos imports sont peut-être incomplets : vérifiez s'il est encore actif."));
                }
            }
        }
        return new SubscriptionAnalysis(increases, redundancies(items), review);
    }

    /** Compare le dernier paiement a la mediane des precedents ; une serie deja tres variable n'a pas de prix de reference. */
    private PriceIncrease priceIncrease(Subscription item, List<LedgerRow> own) {
        if (own.size() < 3) return null;
        LedgerRow latest = own.get(own.size() - 1);
        long[] previous = own.subList(0, own.size() - 1).stream().mapToLong(LedgerRow::amountMinor).sorted().toArray();
        long median = previous[previous.length / 2];
        long delta = latest.amountMinor() - median;
        boolean stable = previous[previous.length - 1] - previous[0] <= median / 4;
        if (!stable || delta < properties.minPriceIncreaseMinor() || delta * 100 < median * properties.priceIncreasePercent()) return null;
        return new PriceIncrease(item.getId(), item.getName(), median, latest.amountMinor(), delta, latest.currency(), List.of(latest.id()));
    }

    private List<PossibleRedundancy> redundancies(List<Subscription> items) {
        List<PossibleRedundancy> result = new ArrayList<>();
        FAMILIES.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(family -> {
            List<Subscription> members = items.stream().filter(item -> {
                String text = (item.getName() + " " + (item.getProvider() == null ? "" : item.getProvider())).toUpperCase(Locale.ROOT);
                return family.getValue().stream().anyMatch(text::contains);
            }).toList();
            if (members.size() >= 2) {
                result.add(new PossibleRedundancy(family.getKey(), members.stream().map(Subscription::getId).toList(),
                        members.stream().map(Subscription::getName).toList(),
                        "Ces services semblent proches. Ils peuvent avoir des contenus différents et rester tous utiles."));
            }
        });
        return result;
    }

    /** Signaux propres aux abonnements. Hausses et chevauchements restent portes par les regles existantes des insights. */
    public List<Signal> signals(UUID userId) {
        List<Signal> signals = new ArrayList<>();
        LocalDate today = today();
        for (Subscription item : tracked(userId)) {
            Cancellation cancellation = repository.latestCancellation(item.getId()).orElse(null);
            Cost cost = ledger.cost(item);
            if (cancellation != null) {
                var verification = cancellations.verify(userId, item, cancellation);
                if (verification.state() == VerificationState.CHARGE_OBSERVED) {
                    signals.add(new Signal("POST_CANCELLATION_CHARGE", item.getId().toString(), "Prélèvement observé après résiliation : " + item.getName(),
                            verification.explanation(), true, "Date de fin retenue : " + verification.referenceDate() + ".", null,
                            "Vérifier auprès du fournisseur ou de votre banque.", verification.chargeTransactionIds(),
                            cost.amountMinor() == null ? 0 : cost.amountMinor()));
                }
                boolean stalePlan = cancellation.status() == CancellationStatus.PLANNED
                        && cancellation.decidedAt().atZone(ZONE).toLocalDate().plusDays(properties.plannedStaleDays()).isBefore(today);
                boolean staleRequest = cancellation.status() == CancellationStatus.REQUESTED && cancellation.requestedOn() != null
                        && cancellation.requestedOn().plusDays(properties.requestedStaleDays()).isBefore(today);
                if (stalePlan || staleRequest) {
                    signals.add(new Signal("CANCELLATION_UNFINISHED", item.getId().toString(), "Résiliation à finaliser : " + item.getName(),
                            stalePlan ? "Vous avez prévu cette résiliation, sans demande enregistrée depuis." : "Aucune confirmation enregistrée pour votre demande.",
                            false, "Démarche au statut " + cancellation.status() + ".", null,
                            "Mettre à jour votre démarche.", List.of(cancellation.id()), cost.known() ? cost.monthlyMinor() : 0));
                }
            }
            boolean open = cancellation != null && Set.of(CancellationStatus.PLANNED, CancellationStatus.REQUESTED, CancellationStatus.CONFIRMED, CancellationStatus.ENDED).contains(cancellation.status());
            if (item.getDeclaredStatus() == SubscriptionStatus.CANCELED || open || !cost.known()) continue;
            LocalDate next = nextDate(item, cost, today);
            boolean annualish = cost.amountMinor() >= properties.importantRenewalMinor();
            if (next != null && annualish && !next.isAfter(today.plusDays(properties.renewalAlertDays()))) {
                signals.add(new Signal("SUBSCRIPTION_RENEWAL", item.getId().toString(), "Renouvellement à examiner : " + item.getName(),
                        "Prochaine échéance " + (next.equals(item.getNextDueDate()) ? "confirmée" : "estimée") + " le " + next + ".", false,
                        "Montant de l'échéance connu.", null, "Décider avant cette date si vous le gardez.", List.of(item.getId()), cost.amountMinor()));
            }
            if (item.isNoLongerNeeded()) {
                signals.add(new Signal("SUBSCRIPTION_SAVINGS", item.getId().toString(), "Économie possible : " + item.getName(),
                        "Vous avez indiqué ne plus en avoir besoin. Sa résiliation libérerait environ " + cost.annualMinor() / 100 + " € par an.", false,
                        "Projection théorique sur 12 mois, pas une économie réalisée.", cost.annualMinor(), "Commencer une démarche de résiliation.", List.of(item.getId()), cost.monthlyMinor()));
            }
        }
        return signals;
    }
}
