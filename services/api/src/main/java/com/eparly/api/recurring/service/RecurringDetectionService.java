package com.eparly.api.recurring.service;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;
import com.eparly.api.recurring.dto.RecurringAnalyzeRequest;
import com.eparly.api.recurring.dto.RecurringAnalyzeResponse;
import com.eparly.api.recurring.repository.RecurringSeriesRepository;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.domain.TransactionType;
import com.eparly.api.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecurringDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringDetectionService.class);

    private static final Set<String> GENERIC_LABELS = Set.of(
            "CB", "CARTE", "PAIEMENT", "VIREMENT", "RETRAIT", "DAB",
            "OPERATION", "ACHAT", "DEBIT", "CREDIT"
    );

    private static final Set<String> SUBSCRIPTION_KEYWORDS = Set.of(
            "NETFLIX", "SPOTIFY", "DEEZER", "DISNEY", "AMAZON PRIME", "APPLE",
            "GOOGLE", "MICROSOFT", "ADOBE", "CANAL", "YOUTUBE", "HBO",
            "PARAMOUNT", "NINTENDO", "PLAYSTATION", "XBOX", "ABONNEMENT",
            "SUBSCRIPTION", "PREMIUM"
    );

    private static final Set<String> BILL_KEYWORDS = Set.of(
            "EDF", "ENGIE", "ORANGE", "SFR", "FREE", "BOUYGUES", "EAU", "GAZ",
            "ELECTRIC", "ENERGIE", "INTERNET", "TELEPHONE", "MOBILE", "LOYER",
            "RENT", "ASSURANCE", "INSURANCE", "MUTUELLE", "IMPOT", "TAX",
            "FACTURE", "BILL", "PRELEVEMENT", "URSSAF", "CPAM", "SNCF"
    );

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringSeriesRepository seriesRepository;

    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastRun = new ConcurrentHashMap<>();

    public RecurringDetectionService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            RecurringSeriesRepository seriesRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.seriesRepository = seriesRepository;
    }

    @Transactional
    public RecurringAnalyzeResponse analyze(UUID userId, RecurringAnalyzeRequest request) {
        List<UUID> requested = request != null && request.accountIds() != null ? request.accountIds() : List.of();
        boolean force = request != null && Boolean.TRUE.equals(request.force());

        // Resolve accounts
        List<Account> accounts;
        if (requested.isEmpty()) {
            accounts = accountRepository.findAllByUserId(userId);
        } else {
            accounts = new ArrayList<>();
            for (UUID aid : requested) {
                Account acc = accountRepository.findByIdAndUserId(aid, userId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
                accounts.add(acc);
            }
        }

        Object lock = locks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            // Time-based rate limiting removed to allow repeated analyses after imports;
            // concurrency is still protected by the per-user lock above.

            int transactionsAnalyzed = 0;
            int created = 0;
            int updated = 0;
            int candidates = 0;

            Instant now = Instant.now();

            for (Account account : accounts) {
                List<Transaction> txs = transactionRepository.findAllByAccountIdOrderByBookingDateDesc(account.getId());
                transactionsAnalyzed += txs.size();

                Map<String, List<Transaction>> groups = new LinkedHashMap<>();
                for (Transaction t : txs) {
                    if (!isCandidate(t)) continue;
                    String currency = t.getCurrency() != null ? t.getCurrency() : account.getCurrency();
                    String key = t.getNormalizedLabel() + "##CUR##" + currency;
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
                }

                for (Map.Entry<String, List<Transaction>> entry : groups.entrySet()) {
                    String[] parts = entry.getKey().split("##CUR##", 2);
                    String label = parts[0];
                    String currency = parts.length > 1 ? parts[1] : account.getCurrency();
                    List<Transaction> group = entry.getValue();
                    group.sort(Comparator.comparing(Transaction::getBookingDate)
                            .thenComparing(t -> t.getId() == null ? UUID.randomUUID() : t.getId()));

                    // dedup by date+amount
                    List<Transaction> occ = dedupOccurrences(group);
                    if (occ.size() < 3) continue;

                    // compute intervals
                    List<Long> intervals = new ArrayList<>();
                    for (int i = 1; i < occ.size(); i++) {
                        intervals.add((long) java.time.temporal.ChronoUnit.DAYS.between(
                                occ.get(i - 1).getBookingDate(), occ.get(i).getBookingDate()));
                    }
                    if (intervals.stream().anyMatch(d -> d <= 0)) continue;

                    long medianInterval = medianLong(intervals);
                    RecurringFrequency freq = classifyFrequency(medianInterval);
                    // consistency
                    boolean consistent = isConsistent(intervals, medianInterval, freq);
                    if (!consistent) continue;

                    // amount stats (absolute)
                    List<Long> amounts = new ArrayList<>();
                    for (Transaction t : occ) {
                        amounts.add(Math.abs(t.getAmountMinor()));
                    }
                    long minAmt = amounts.stream().min(Long::compareTo).orElse(0L);
                    long maxAmt = amounts.stream().max(Long::compareTo).orElse(0L);
                    long usualAmt = medianLong(amounts);
                    boolean amountStable = (maxAmt - minAmt) <= Math.max(500L, (long) (usualAmt * 0.1));

                    // confidence
                    int score = 40;
                    if (occ.size() >= 4) score += 10;
                    if (occ.size() >= 6) score += 10;
                    if (freq != RecurringFrequency.UNKNOWN) score += 15;
                    if (consistent) score += 15;
                    if (amountStable) score += 15;
                    if (label.length() >= 8 && !GENERIC_LABELS.contains(label)) score += 10;
                    if (occ.get(0).getTransactionType() == TransactionType.EXPENSE) score += 5;
                    if (occ.stream().anyMatch(t -> t.getCategory() != null)) score += 5;
                    score = Math.min(100, Math.max(0, score));
                    if (score < 40) continue;

                    // classification
                    RecurringSeriesType type = classifyType(label, freq, amountStable, score);

                    // reasons
                    List<String> reasons = new ArrayList<>();
                    reasons.add(occ.size() + " occurrence(s) detected on distinct dates");
                    reasons.add("Estimated frequency: " + freq + " (median interval " + medianInterval + " days)");
                    reasons.add("Amounts: min=" + minAmt + ", max=" + maxAmt + ", usual=" + usualAmt + " minor units");
                    if (amountStable) reasons.add("Amounts are stable (variation within tolerance)");
                    else reasons.add("Amounts vary significantly");
                    reasons.add("Normalized label: " + label);
                    reasons.add("Confidence score is a heuristic estimate, not a calibrated probability");

                    // monthly equivalent
                    BigDecimal monthlyEq = computeMonthlyEquivalent(usualAmt, freq, medianInterval);

                    // next estimated date
                    LocalDate nextDate = occ.get(occ.size() - 1).getBookingDate().plusDays(medianInterval);

                    // upsert
                    RecurringSeries existing = seriesRepository
                            .findByAccountAndLabelAndCurrency(account.getId(), label, currency)
                            .orElse(null);

                    if (existing != null) {
                        if (existing.getStatus() == RecurringSeriesStatus.DISMISSED) {
                            // reactivate only if new significant reason
                            boolean newSignificant = occ.size() > existing.getDismissedOccurrenceCount()
                                    && occ.get(occ.size() - 1).getBookingDate().isAfter(existing.getUserDecisionAt() != null
                                            ? existing.getUserDecisionAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                            : existing.getLastOccurrenceDate());
                            if (!newSignificant) {
                                continue;
                            }
                            existing.setStatus(RecurringSeriesStatus.CANDIDATE);
                            existing.setDismissedOccurrenceCount(0);
                        }
                        existing.setFrequency(freq);
                        existing.setSeriesType(type);
                        existing.setUsualAmountMinor(usualAmt);
                        existing.setMinAmountMinor(minAmt);
                        existing.setMaxAmountMinor(maxAmt);
                        existing.setOccurrenceCount(occ.size());
                        existing.setFirstOccurrenceDate(occ.get(0).getBookingDate());
                        existing.setLastOccurrenceDate(occ.get(occ.size() - 1).getBookingDate());
                        existing.setNextEstimatedDate(nextDate);
                        existing.setMonthlyEquivalentMinor(monthlyEq);
                        existing.setConfidenceScore(score);
                        existing.setReasons(new ArrayList<>(reasons));
                        existing.setLastAnalyzedAt(now);
                        existing.getTransactions().clear();
                        existing.getTransactions().addAll(occ);
                        seriesRepository.save(existing);
                        updated++;
                    } else {
                        RecurringSeries s = new RecurringSeries();
                        s.setAccount(account);
                        s.setNormalizedLabel(label);
                        s.setCurrency(currency);
                        s.setFrequency(freq);
                        s.setStatus(RecurringSeriesStatus.CANDIDATE);
                        s.setSeriesType(type);
                        s.setUsualAmountMinor(usualAmt);
                        s.setMinAmountMinor(minAmt);
                        s.setMaxAmountMinor(maxAmt);
                        s.setOccurrenceCount(occ.size());
                        s.setFirstOccurrenceDate(occ.get(0).getBookingDate());
                        s.setLastOccurrenceDate(occ.get(occ.size() - 1).getBookingDate());
                        s.setNextEstimatedDate(nextDate);
                        s.setMonthlyEquivalentMinor(monthlyEq);
                        s.setConfidenceScore(score);
                        s.setReasons(new ArrayList<>(reasons));
                        s.setLastAnalyzedAt(now);
                        s.getTransactions().addAll(occ);
                        seriesRepository.save(s);
                        created++;
                    }

                    if (true) candidates++;
                }
            }

            // count candidates overall for user
            long candCount = seriesRepository.findAllByUserId(userId).stream()
                    .filter(s -> s.getStatus() == RecurringSeriesStatus.CANDIDATE).count();

            return new RecurringAnalyzeResponse(
                    accounts.size(),
                    transactionsAnalyzed,
                    created,
                    updated,
                    (int) candCount,
                    Instant.now(),
                    "Analysis completed. Results are heuristic estimates based on imported CSV data.",
                    created,
                    updated,
                    (int) candCount,
                    transactionsAnalyzed,
                    accounts.size()
            );
        }
    }

    private boolean isCandidate(Transaction t) {
        if (t == null || t.getBookingDate() == null || t.getAmountMinor() == null) return false;
        if (t.getTransactionType() == TransactionType.TRANSFER) return false;
        if (t.getTransactionType() == TransactionType.REFUND) return false;
        if (t.getTransactionType() == TransactionType.INCOME) return false;
        boolean expenseLike = t.getTransactionType() == TransactionType.EXPENSE
                || t.getTransactionType() == TransactionType.FEE
                || (t.getTransactionType() == TransactionType.UNKNOWN && t.getAmountMinor() < 0);
        if (!expenseLike) return false;
        String label = t.getNormalizedLabel();
        if (label == null) return false;
        String trimmed = label.trim();
        if (trimmed.length() < 3) return false;
        String up = trimmed.toUpperCase();
        if (GENERIC_LABELS.contains(up)) return false;
        String[] excludedFragments = {"VIREMENT", "TRANSFER", "REMBOURSEMENT", "REFUND", "SALAIRE", "SALARY", "RETRAIT", "DAB"};
        for (String frag : excludedFragments) {
            if (up.contains(frag)) return false;
        }
        return true;
    }

    private List<Transaction> dedupOccurrences(List<Transaction> group) {
        List<Transaction> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Transaction t : group) {
            String key = t.getBookingDate() + "|" + t.getAmountMinor() + "|" + t.getFingerprint();
            // collapse exact same date+amount duplicates
            String collapseKey = t.getBookingDate() + "|" + t.getAmountMinor();
            if (seen.contains(collapseKey)) continue;
            seen.add(collapseKey);
            result.add(t);
        }
        return result;
    }

    private RecurringFrequency classifyFrequency(long medianDays) {
        if (medianDays >= 5 && medianDays <= 9) return RecurringFrequency.WEEKLY;
        if (medianDays >= 25 && medianDays <= 35) return RecurringFrequency.MONTHLY;
        if (medianDays >= 80 && medianDays <= 100) return RecurringFrequency.QUARTERLY;
        if (medianDays >= 350 && medianDays <= 380) return RecurringFrequency.YEARLY;
        return RecurringFrequency.UNKNOWN;
    }

    private boolean isConsistent(List<Long> intervals, long median, RecurringFrequency freq) {
        int tol;
        switch (freq) {
            case WEEKLY -> tol = 2;
            case MONTHLY -> tol = 5;
            case QUARTERLY -> tol = 10;
            case YEARLY -> tol = 15;
            default -> tol = 3;
        }
        for (long iv : intervals) {
            if (Math.abs(iv - median) > tol) {
                // allow cv fallback
                double mean = intervals.stream().mapToLong(x -> x).average().orElse(0);
                double var = 0;
                for (long iv2 : intervals) var += Math.pow(iv2 - mean, 2);
                var /= intervals.size();
                double cv = mean > 0 ? Math.sqrt(var) / mean : 1;
                if (cv > 0.15) return false;
                // if any single interval exceeds 2*tol, reject
                if (Math.abs(iv - median) > tol * 2) return false;
            }
        }
        return true;
    }

    private RecurringSeriesType classifyType(String label, RecurringFrequency freq, boolean amountStable, int score) {
        String up = label.toUpperCase();
        for (String kw : SUBSCRIPTION_KEYWORDS) {
            if (up.contains(kw)) return RecurringSeriesType.SUBSCRIPTION;
        }
        for (String kw : BILL_KEYWORDS) {
            if (up.contains(kw)) return RecurringSeriesType.RECURRING_BILL;
        }
        if (score < 50) return RecurringSeriesType.UNCLASSIFIED;
        return RecurringSeriesType.OTHER_RECURRING;
    }

    private BigDecimal computeMonthlyEquivalent(long usualMinor, RecurringFrequency freq, long medianInterval) {
        BigDecimal usual = BigDecimal.valueOf(usualMinor);
        BigDecimal factor;
        switch (freq) {
            case WEEKLY -> factor = new BigDecimal("52").divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);
            case MONTHLY -> factor = BigDecimal.ONE;
            case QUARTERLY -> factor = new BigDecimal("1").divide(new BigDecimal("3"), 6, RoundingMode.HALF_UP);
            case YEARLY -> factor = new BigDecimal("1").divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);
            default -> {
                if (medianInterval > 0) {
                    factor = new BigDecimal("30").divide(BigDecimal.valueOf(medianInterval), 6, RoundingMode.HALF_UP);
                } else {
                    factor = BigDecimal.ONE;
                }
            }
        }
        return usual.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    private long medianLong(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int n = sorted.size();
        if (n == 0) return 0;
        if (n % 2 == 1) return sorted.get(n / 2);
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
}
