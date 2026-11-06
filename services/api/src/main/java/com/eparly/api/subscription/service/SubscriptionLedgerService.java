package com.eparly.api.subscription.service;

import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.repository.RecurringSeriesRepository;
import com.eparly.api.recurring.service.LabelKey;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionModels.Cost;
import com.eparly.api.subscription.domain.SubscriptionModels.LedgerRow;
import com.eparly.api.subscription.repository.SubscriptionTrackingRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Rapproche un abonnement de ses transactions, y compris celles importees apres la derniere analyse : la verification
 * apres resiliation ne doit pas dependre d'une nouvelle detection. Une serie compte par compte et par cle de libelle.
 */
@Service
public class SubscriptionLedgerService {
    private static final int MIN_NAME_MATCH_LENGTH = 4;

    private final SubscriptionTrackingRepository repository;
    private final RecurringSeriesRepository seriesRepository;

    public SubscriptionLedgerService(SubscriptionTrackingRepository repository, RecurringSeriesRepository seriesRepository) {
        this.repository = repository;
        this.seriesRepository = seriesRepository;
    }

    public List<RecurringSeries> series(Subscription subscription) {
        List<RecurringSeries> all = new ArrayList<>();
        if (subscription.getRecurringSeries() != null) all.add(subscription.getRecurringSeries());
        seriesRepository.findAllById(repository.linkedSeriesIds(subscription.getId())).forEach(all::add);
        return all;
    }

    public List<LedgerRow> rows(Subscription subscription, List<LedgerRow> userRows) {
        List<RecurringSeries> series = series(subscription);
        if (!series.isEmpty()) {
            return userRows.stream().filter(row -> series.stream().anyMatch(item -> item.getAccount().getId().equals(row.accountId())
                    && item.getCurrency().equals(row.currency())
                    && LabelKey.of(item.getNormalizedLabel()).equals(LabelKey.of(row.label())))).toList();
        }
        String name = LabelKey.of(subscription.getName().toUpperCase(Locale.ROOT));
        if (name.length() < MIN_NAME_MATCH_LENGTH) return List.of();
        UUID account = subscription.getAccount() == null ? null : subscription.getAccount().getId();
        return userRows.stream().filter(row -> (account == null || account.equals(row.accountId()))
                && subscription.getCurrency().equals(row.currency())
                && (" " + LabelKey.of(row.label()) + " ").contains(" " + name + " ")).toList();
    }

    public Cost cost(Subscription subscription) {
        RecurringSeries series = subscription.getRecurringSeries();
        Long amount = subscription.getDeclaredAmountMinor() != null ? subscription.getDeclaredAmountMinor()
                : series == null ? null : series.getUsualAmountMinor();
        RecurringFrequency frequency = subscription.getDeclaredFrequency();
        if ((frequency == null || frequency == RecurringFrequency.UNKNOWN) && series != null) frequency = series.getFrequency();
        return new Cost(amount, frequency == null ? RecurringFrequency.UNKNOWN : frequency, subscription.getCurrency());
    }
}
