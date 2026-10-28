package com.eparly.api.subscription.service;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.repository.RecurringSeriesRepository;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.domain.SubscriptionStatusHistory;
import com.eparly.api.subscription.dto.CreateSubscriptionRequest;
import com.eparly.api.subscription.dto.SubscriptionResponse;
import com.eparly.api.subscription.dto.SubscriptionSummaryResponse;
import com.eparly.api.subscription.dto.UpdateSubscriptionRequest;
import com.eparly.api.subscription.dto.UpdateSubscriptionStatusRequest;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.subscription.repository.SubscriptionStatusHistoryRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository repository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final AccountRepository accountRepository;
    private final RecurringSeriesRepository recurringSeriesRepository;
    private final UserRepository userRepository;

    public SubscriptionService(SubscriptionRepository repository,
                               SubscriptionStatusHistoryRepository historyRepository,
                               AccountRepository accountRepository,
                               RecurringSeriesRepository recurringSeriesRepository,
                               UserRepository userRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.accountRepository = accountRepository;
        this.recurringSeriesRepository = recurringSeriesRepository;
        this.userRepository = userRepository;
    }

    public List<SubscriptionResponse> list(UUID userId, UUID accountId, SubscriptionStatus status) {
        requireActiveUser(userId);
        if (accountId != null) {
            boolean ok = accountRepository.existsByIdAndUserId(accountId, userId);
            if (!ok) throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return repository.findAllByUserIdFiltered(userId, accountId, status).stream()
                .map(this::toResponse)
                .toList();
    }

    public SubscriptionResponse get(UUID userId, UUID id) {
        requireActiveUser(userId);
        Subscription s = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));
        return toResponse(s);
    }

    @Transactional
    public SubscriptionResponse create(UUID userId, CreateSubscriptionRequest req) {
        User user = requireActiveUser(userId);

        Account account = null;
        if (req != null && req.accountId() != null) {
            account = accountRepository.findByIdAndUserId(req.accountId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        }

        RecurringSeries series = null;
        if (req != null && req.recurringSeriesId() != null) {
            series = recurringSeriesRepository.findByIdAndUserId(req.recurringSeriesId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring series not found"));
            if (series.getStatus() == RecurringSeriesStatus.DISMISSED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transform a dismissed series");
            }
            if (repository.existsByRecurringSeriesId(series.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "Recurring series already linked to a subscription");
            }
            // auto-confirm the series if needed
            if (series.getStatus() != RecurringSeriesStatus.CONFIRMED) {
                series.setStatus(RecurringSeriesStatus.CONFIRMED);
                series.setUserDecisionAt(Instant.now());
                if (series.getDecisionNote() == null) series.setDecisionNote("Confirmed via subscription management");
                recurringSeriesRepository.save(series);
            }
            if (account == null) account = series.getAccount();
        }

        String requestedName = req == null ? null : req.name();
        String name;
        if (requestedName == null || requestedName.trim().isEmpty()) {
            if (series != null) name = series.getNormalizedLabel();
            else throw new ApiException(HttpStatus.BAD_REQUEST, "name is required");
        } else {
            name = requestedName.trim();
        }
        if (name == null || name.trim().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "name is required");
        name = name.trim();
        if (name.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "name too long");

        // currency
        String currency;
        if (req.currency() != null && !req.currency().trim().isEmpty()) {
            currency = req.currency().trim().toUpperCase(java.util.Locale.ROOT);
        } else if (series != null) {
            currency = series.getCurrency();
        } else if (account != null) {
            currency = account.getCurrency();
        } else {
            currency = "EUR";
        }
        if (currency.length() != 3) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid currency");

        // amount
        Long amount = req.declaredAmountMinor();
        if (amount == null && series != null) amount = series.getUsualAmountMinor();
        if (amount == null) throw new ApiException(HttpStatus.BAD_REQUEST, "declaredAmountMinor is required");
        if (amount < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "declaredAmountMinor must be non-negative");

        // frequency
        RecurringFrequency freq = req.declaredFrequency();
        if (freq == null) {
            freq = series != null ? series.getFrequency() : RecurringFrequency.UNKNOWN;
        }

        SubscriptionStatus status = req.declaredStatus() != null ? req.declaredStatus() : SubscriptionStatus.ACTIVE;

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setAccount(account);
        sub.setRecurringSeries(series);
        sub.setName(name);
        sub.setProvider(req.provider() == null ? null : req.provider().trim());
        sub.setDeclaredAmountMinor(amount);
        sub.setCurrency(currency);
        sub.setDeclaredFrequency(freq);
        sub.setNextDueDate(req.nextDueDate());
        sub.setNotes(req.notes());
        sub.setDeclaredStatus(status);
        sub.setOrigin(series != null ? SubscriptionOrigin.AUTO_DETECTED : SubscriptionOrigin.USER_DECLARED);
        sub.setConfirmedAt(Instant.now());
        sub.setStatusChangedAt(Instant.now());

        Subscription saved = repository.saveAndFlush(sub);

        // history
        SubscriptionStatusHistory h = new SubscriptionStatusHistory();
        h.setSubscription(saved);
        h.setFromStatus(null);
        h.setToStatus(status);
        h.setChangedAt(Instant.now());
        h.setNote("Subscription created");
        historyRepository.save(h);

        return toResponse(saved);
    }

    @Transactional
    public SubscriptionResponse update(UUID userId, UUID id, UpdateSubscriptionRequest req) {
        requireActiveUser(userId);
        Subscription sub = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));

        if (req.name() != null) {
            String name = req.name().trim();
            if (name.isEmpty() || name.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid name");
            sub.setName(name);
        }
        if (req.provider() != null) {
            sub.setProvider(req.provider().trim().isEmpty() ? null : req.provider().trim());
        }
        if (req.declaredAmountMinor() != null) {
            if (req.declaredAmountMinor() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid amount");
            sub.setDeclaredAmountMinor(req.declaredAmountMinor());
        }
        if (req.currency() != null && !req.currency().trim().isEmpty()) {
            String c = req.currency().trim().toUpperCase(java.util.Locale.ROOT);
            if (c.length() != 3) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid currency");
            sub.setCurrency(c);
        }
        if (req.declaredFrequency() != null) {
            sub.setDeclaredFrequency(req.declaredFrequency());
        }
        if (req.nextDueDate() != null) {
            sub.setNextDueDate(req.nextDueDate());
        }
        // allow clearing notes? if null leave; to clear pass empty? We'll set if provided (including empty -> null)
        if (req.notes() != null) {
            sub.setNotes(req.notes().trim().isEmpty() ? null : req.notes().trim());
        }
        // nextDueDate clearing not supported via null ambiguity; keep.

        return toResponse(repository.saveAndFlush(sub));
    }

    @Transactional
    public SubscriptionResponse updateStatus(UUID userId, UUID id, UpdateSubscriptionStatusRequest req) {
        requireActiveUser(userId);
        Subscription sub = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));

        SubscriptionStatus from = sub.getDeclaredStatus();
        SubscriptionStatus to = req.status();
        if (to == null) throw new ApiException(HttpStatus.BAD_REQUEST, "status is required");

        // state machine: allow any explicit transition among ACTIVE/PAUSED/CANCELED/UNKNOWN
        sub.setDeclaredStatus(to);
        sub.setStatusChangedAt(Instant.now());
        repository.save(sub);

        SubscriptionStatusHistory h = new SubscriptionStatusHistory();
        h.setSubscription(sub);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedAt(Instant.now());
        h.setNote(req.note());
        historyRepository.save(h);

        return toResponse(sub);
    }

    public SubscriptionSummaryResponse summary(UUID userId, UUID accountId) {
        requireActiveUser(userId);
        if (accountId != null) {
            boolean ok = accountRepository.existsByIdAndUserId(accountId, userId);
            if (!ok) throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        List<Subscription> subs = repository.findAllByUserIdFiltered(userId, accountId, null);

        Map<String, List<Subscription>> byCurrency = new LinkedHashMap<>();
        for (Subscription s : subs) {
            byCurrency.computeIfAbsent(s.getCurrency(), k -> new ArrayList<>()).add(s);
        }

        List<SubscriptionSummaryResponse.CurrencySummary> currencies = new ArrayList<>();
        int total = subs.size();
        for (Map.Entry<String, List<Subscription>> e : byCurrency.entrySet()) {
            String currency = e.getKey();
            BigDecimal monthlyTotal = BigDecimal.ZERO;
            BigDecimal annualTotal = BigDecimal.ZERO;
            int toVerify = 0;
            for (Subscription s : e.getValue()) {
                BigDecimal[] costs = computeCosts(s);
                monthlyTotal = monthlyTotal.add(costs[0]);
                annualTotal = annualTotal.add(costs[1]);
                if (s.getDeclaredStatus() == SubscriptionStatus.UNKNOWN || isContradictory(s)) {
                    toVerify++;
                }
            }
            currencies.add(new SubscriptionSummaryResponse.CurrencySummary(
                    currency,
                    e.getValue().size(),
                    monthlyTotal.setScale(4, RoundingMode.HALF_UP),
                    annualTotal.setScale(4, RoundingMode.HALF_UP),
                    toVerify
            ));
        }

        return new SubscriptionSummaryResponse(total, currencies);
    }

    private User requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Account is not active"));
    }

    private BigDecimal[] computeCosts(Subscription s) {
        RecurringSeries series = s.getRecurringSeries();

        Long amount;
        String source;
        if (s.getDeclaredAmountMinor() != null) {
            amount = s.getDeclaredAmountMinor();
            source = "DECLARED";
        } else if (series != null) {
            amount = series.getUsualAmountMinor();
            source = "OBSERVED";
        } else {
            amount = 0L;
            source = "NONE";
        }

        RecurringFrequency freq = s.getDeclaredFrequency();
        if (freq == null || freq == RecurringFrequency.UNKNOWN) {
            if (series != null && series.getFrequency() != null && series.getFrequency() != RecurringFrequency.UNKNOWN) {
                freq = series.getFrequency();
            } else {
                freq = RecurringFrequency.UNKNOWN;
            }
        }

        BigDecimal monthly = computeMonthlyEquivalent(amount, freq, series);
        BigDecimal annual = computeAnnualEquivalent(amount, freq, series);
        return new BigDecimal[]{monthly, annual};
    }

    private BigDecimal computeMonthlyEquivalent(long amountMinor, RecurringFrequency freq, RecurringSeries series) {
        BigDecimal amount = BigDecimal.valueOf(amountMinor);
        BigDecimal factor;
        switch (freq) {
            case WEEKLY -> factor = new BigDecimal("52").divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);
            case MONTHLY -> factor = BigDecimal.ONE;
            case QUARTERLY -> factor = new BigDecimal("1").divide(new BigDecimal("3"), 6, RoundingMode.HALF_UP);
            case YEARLY -> factor = new BigDecimal("1").divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);
            default -> {
                if (series != null && series.getLastOccurrenceDate() != null && series.getFirstOccurrenceDate() != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(series.getFirstOccurrenceDate(), series.getLastOccurrenceDate());
                    int occ = series.getOccurrenceCount() == null ? 0 : series.getOccurrenceCount();
                    long interval = occ > 1 ? Math.max(1, days / (occ - 1)) : 30;
                    if (interval > 0) {
                        factor = new BigDecimal("30").divide(BigDecimal.valueOf(interval), 6, RoundingMode.HALF_UP);
                    } else {
                        factor = BigDecimal.ONE;
                    }
                } else {
                    factor = BigDecimal.ONE;
                }
            }
        }
        return amount.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAnnualEquivalent(long amountMinor, RecurringFrequency freq, RecurringSeries series) {
        BigDecimal amount = BigDecimal.valueOf(amountMinor);
        BigDecimal factor;
        switch (freq) {
            case WEEKLY -> factor = new BigDecimal("52");
            case MONTHLY -> factor = new BigDecimal("12");
            case QUARTERLY -> factor = new BigDecimal("4");
            case YEARLY -> factor = BigDecimal.ONE;
            default -> {
                if (series != null && series.getFirstOccurrenceDate() != null && series.getLastOccurrenceDate() != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(series.getFirstOccurrenceDate(), series.getLastOccurrenceDate());
                    int occ = series.getOccurrenceCount() == null ? 0 : series.getOccurrenceCount();
                    long interval = occ > 1 ? Math.max(1, days / (occ - 1)) : 30;
                    if (interval > 0) {
                        factor = new BigDecimal("365").divide(BigDecimal.valueOf(interval), 6, RoundingMode.HALF_UP);
                    } else {
                        factor = new BigDecimal("12");
                    }
                } else {
                    factor = new BigDecimal("12");
                }
            }
        }
        return amount.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    private boolean isContradictory(Subscription s) {
        RecurringSeries series = s.getRecurringSeries();
        if (series == null) return false;
        if (s.getDeclaredStatus() != SubscriptionStatus.CANCELED) return false;
        LocalDate lastPayment = series.getLastOccurrenceDate();
        if (lastPayment == null) return false;
        LocalDate cancelDate = s.getStatusChangedAt().atZone(ZoneId.systemDefault()).toLocalDate();
        return lastPayment.isAfter(cancelDate);
    }

    private SubscriptionResponse toResponse(Subscription s) {
        RecurringSeries series = s.getRecurringSeries();

        Long observedUsual = series != null ? series.getUsualAmountMinor() : null;
        RecurringFrequency observedFreq = series != null ? series.getFrequency() : null;
        LocalDate observedLastPayment = series != null ? series.getLastOccurrenceDate() : null;
        LocalDate observedNextEstimated = series != null ? series.getNextEstimatedDate() : null;
        BigDecimal observedMonthly = series != null ? series.getMonthlyEquivalentMinor() : null;
        Integer observedConfidence = series != null ? series.getConfidenceScore() : null;

        BigDecimal[] costs = computeCosts(s);
        BigDecimal monthly = costs[0];
        BigDecimal annual = costs[1];

        // determine cost amount source & amount
        Long costAmount;
        String costSource;
        if (s.getDeclaredAmountMinor() != null) {
            costAmount = s.getDeclaredAmountMinor();
            costSource = "DECLARED";
        } else if (series != null) {
            costAmount = series.getUsualAmountMinor();
            costSource = "OBSERVED";
        } else {
            costAmount = 0L;
            costSource = "NONE";
        }

        RecurringFrequency costFreq = s.getDeclaredFrequency();
        if (costFreq == null || costFreq == RecurringFrequency.UNKNOWN) {
            if (series != null && series.getFrequency() != null && series.getFrequency() != RecurringFrequency.UNKNOWN) {
                costFreq = series.getFrequency();
            } else {
                costFreq = RecurringFrequency.UNKNOWN;
            }
        }

        LocalDate indicativeNext = s.getNextDueDate() != null ? s.getNextDueDate()
                : (series != null ? series.getNextEstimatedDate() : null);

        boolean contradictory = isContradictory(s);

        return new SubscriptionResponse(
                s.getId(),
                s.getAccount() != null ? s.getAccount().getId() : null,
                s.getAccount() != null ? s.getAccount().getName() : null,
                series != null ? series.getId() : null,
                s.getName(),
                s.getProvider(),
                s.getDeclaredAmountMinor(),
                s.getCurrency(),
                s.getDeclaredFrequency(),
                s.getDeclaredStatus(),
                s.getOrigin(),
                s.getNextDueDate(),
                s.getConfirmedAt(),
                s.getNotes(),
                s.getStatusChangedAt(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                observedUsual,
                observedFreq,
                observedLastPayment,
                observedNextEstimated,
                observedMonthly,
                observedConfidence,
                costAmount,
                costSource,
                costFreq,
                monthly,
                annual,
                observedLastPayment,
                indicativeNext,
                contradictory,
                s.getDeclaredStatus(),
                s.getDeclaredFrequency(),
                s.getDeclaredAmountMinor(),
                observedLastPayment,
                observedNextEstimated,
                observedUsual
        );
    }
}
