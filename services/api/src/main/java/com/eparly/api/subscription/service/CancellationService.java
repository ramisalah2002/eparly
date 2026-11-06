package com.eparly.api.subscription.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.insights.service.CommercialAlternativesService;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.subscription.config.SubscriptionTrackingProperties;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionModels;
import com.eparly.api.subscription.domain.SubscriptionModels.Cancellation;
import com.eparly.api.subscription.domain.SubscriptionModels.CancellationStatus;
import com.eparly.api.subscription.domain.SubscriptionModels.Cost;
import com.eparly.api.subscription.domain.SubscriptionModels.LedgerRow;
import com.eparly.api.subscription.domain.SubscriptionModels.VerificationState;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.CancellationResponse;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.ConfirmCancellationRequest;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.StartCancellationRequest;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionEvent;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionSavings;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SuspectCharge;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.UpdateCancellationRequest;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.Verification;
import com.eparly.api.subscription.dto.UpdateSubscriptionStatusRequest;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.subscription.repository.SubscriptionTrackingRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Suivi d'une demarche menee par l'utilisateur. Eparly n'envoie rien au fournisseur : enregistrer une demande ne prouve
 * pas qu'il l'a recue, et une absence de prelevement ne prouve pas une resiliation.
 */
@Service
@Transactional(readOnly = true)
public class CancellationService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final int HISTORY_YEARS = 3;
    private static final Set<CancellationStatus> FOLLOWED =
            Set.of(CancellationStatus.REQUESTED, CancellationStatus.CONFIRMED, CancellationStatus.ENDED);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionTrackingRepository repository;
    private final SubscriptionLedgerService ledger;
    private final SubscriptionService subscriptionService;
    private final CommercialAlternativesService alternatives;
    private final SubscriptionTrackingProperties properties;
    private final Clock clock;

    public CancellationService(SubscriptionRepository subscriptions, SubscriptionTrackingRepository repository,
                               SubscriptionLedgerService ledger, SubscriptionService subscriptionService,
                               CommercialAlternativesService alternatives, SubscriptionTrackingProperties properties, Clock clock) {
        this.subscriptions = subscriptions;
        this.repository = repository;
        this.ledger = ledger;
        this.subscriptionService = subscriptionService;
        this.alternatives = alternatives;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CancellationResponse start(UUID userId, UUID subscriptionId, StartCancellationRequest request) {
        Subscription subscription = require(userId, subscriptionId);
        if (subscription.getArchivedAt() != null) throw new ApiException(HttpStatus.CONFLICT, "Subscription is archived");
        Cost cost = ledger.cost(subscription);
        try {
            repository.insertCancellation(subscriptionId, clock.instant(), request == null ? null : request.notes(),
                    cost.amountMinor(), cost.frequency(), cost.currency());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "A cancellation is already in progress");
        }
        repository.event(subscriptionId, "CANCELLATION_STATUS_CHANGED", details("from", null, "to", CancellationStatus.PLANNED), null);
        return get(userId, subscriptionId);
    }

    public CancellationResponse get(UUID userId, UUID subscriptionId) {
        Subscription subscription = require(userId, subscriptionId);
        Cancellation cancellation = repository.latestCancellation(subscriptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cancellation not found"));
        return response(subscription, cancellation, verify(userId, subscription, cancellation));
    }

    @Transactional
    public CancellationResponse update(UUID userId, UUID subscriptionId, UpdateCancellationRequest request) {
        Subscription subscription = require(userId, subscriptionId);
        Cancellation current = current(subscriptionId);
        CancellationStatus next = request.status() == null ? current.status() : request.status();
        if (next == CancellationStatus.CONFIRMED && current.status() != CancellationStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use the confirmation endpoint to record a confirmation");
        }
        requireTransition(current.status(), next);
        LocalDate requestedOn = request.requestedOn() != null ? request.requestedOn() : current.requestedOn();
        requireNotFuture(requestedOn);
        if (next == CancellationStatus.REQUESTED && requestedOn == null) {
            // La date n'est jamais supposee : seule la personne qui a fait la demarche la connait.
            throw new ApiException(HttpStatus.BAD_REQUEST, "requestedOn is required");
        }
        Cancellation updated = new Cancellation(current.id(), current.subscriptionId(), next, current.decidedAt(), requestedOn,
                request.channel() != null ? request.channel() : current.channel(),
                request.requestReference() != null ? request.requestReference() : current.requestReference(),
                request.providerEndDate() != null ? request.providerEndDate() : current.providerEndDate(),
                current.confirmedOn(), current.confirmationSource(), current.confirmationReference(), false,
                request.notes() != null ? request.notes() : current.notes(), current.baselineAmountMinor(),
                current.baselineFrequency(), current.baselineCurrency(), current.version(), current.updatedAt());
        apply(userId, subscription, current, updated, request.expectedVersion());
        return get(userId, subscriptionId);
    }

    @Transactional
    public CancellationResponse confirm(UUID userId, UUID subscriptionId, ConfirmCancellationRequest request) {
        Subscription subscription = require(userId, subscriptionId);
        Cancellation current = current(subscriptionId);
        requireTransition(current.status(), CancellationStatus.CONFIRMED);
        requireNotFuture(request.confirmedOn());
        Cancellation updated = new Cancellation(current.id(), current.subscriptionId(), CancellationStatus.CONFIRMED, current.decidedAt(),
                current.requestedOn(), current.channel(), current.requestReference(),
                request.providerEndDate() != null ? request.providerEndDate() : current.providerEndDate(),
                request.confirmedOn(), request.source(), request.reference(), false, current.notes(),
                current.baselineAmountMinor(), current.baselineFrequency(), current.baselineCurrency(), current.version(), current.updatedAt());
        apply(userId, subscription, current, updated, request.expectedVersion());
        return get(userId, subscriptionId);
    }

    private void apply(UUID userId, Subscription subscription, Cancellation current, Cancellation updated, long expectedVersion) {
        if (repository.updateCancellation(updated, expectedVersion) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Cancellation has changed; reload it");
        }
        if (current.status() != updated.status()) {
            repository.event(subscription.getId(), "CANCELLATION_STATUS_CHANGED",
                    details("from", current.status(), "to", updated.status(), "source", updated.confirmationSource()), null);
            if (updated.status() == CancellationStatus.ENDED) {
                subscriptionService.updateStatus(userId, subscription.getId(), new UpdateSubscriptionStatusRequest(
                        SubscriptionStatus.CANCELED, "Demarche de resiliation terminee, declaree par l'utilisateur"));
            }
        } else {
            repository.event(subscription.getId(), "CANCELLATION_UPDATED", details("status", updated.status()), null);
        }
    }

    public List<SubscriptionEvent> history(UUID userId, UUID subscriptionId) {
        require(userId, subscriptionId);
        return repository.events(subscriptionId).stream()
                .map(event -> new SubscriptionEvent(event.id(), event.type(), event.details(), event.occurredAt())).toList();
    }

    /** Lit les prelevements suspects apres avoir enregistre les nouveaux : l'operation est idempotente. */
    @Transactional
    public List<SuspectCharge> suspectCharges(UUID userId) {
        recordSuspectCharges(userId);
        return repository.postCancellationCharges(userId).stream()
                .map(event -> new SuspectCharge(event.id(), event.subscriptionId(), event.details(), event.occurredAt())).toList();
    }

    /** Evenement metier par transaction, pret pour le futur moteur de notifications. La cle unique evite tout doublon. */
    @Transactional
    public void recordSuspectCharges(UUID userId) {
        for (Subscription subscription : subscriptions.findAllByUserIdFiltered(userId, null, null)) {
            Cancellation cancellation = repository.latestCancellation(subscription.getId()).orElse(null);
            if (cancellation == null) continue;
            Verification verification = verify(userId, subscription, cancellation);
            if (verification.state() != VerificationState.CHARGE_OBSERVED) continue;
            for (UUID transactionId : verification.chargeTransactionIds()) {
                repository.event(subscription.getId(), "POST_CANCELLATION_CHARGE",
                        details("transactionId", transactionId, "cancellationId", cancellation.id(),
                                "referenceDate", verification.referenceDate(), "amountChanged", verification.amountChanged(),
                                "unexpectedRenewal", cancellation.baselineFrequency() == RecurringFrequency.YEARLY),
                        "post-cancel|" + cancellation.id() + "|" + transactionId);
            }
        }
    }

    public SubscriptionSavings savings(UUID userId, UUID subscriptionId) {
        Subscription subscription = require(userId, subscriptionId);
        Cost liveCost = ledger.cost(subscription);
        Cancellation cancellation = repository.latestCancellation(subscriptionId).orElse(null);
        Cost cost = cancellation == null ? liveCost
                : new Cost(cancellation.baselineAmountMinor(), cancellation.baselineFrequency(), cancellation.baselineCurrency());
        Long annual = cost.known() ? cost.annualMinor() : null;
        CancellationStatus status = cancellation == null ? null : cancellation.status();
        boolean planned = status == CancellationStatus.PLANNED || status == CancellationStatus.REQUESTED;
        boolean confirmed = status == CancellationStatus.CONFIRMED || status == CancellationStatus.ENDED;
        List<String> assumptions = new ArrayList<>();
        assumptions.add(cancellation == null ? "Cout actuel de l'abonnement, sur 12 mois." : "Cout fige a la decision de resilier, sur 12 mois.");
        assumptions.add("Hors frais de resiliation ou de fin d'engagement, non connus d'Eparly.");
        Long observed = null;
        int cycles = 0;
        if (confirmed && cost.known()) {
            Verification verification = verify(userId, subscription, cancellation);
            int required = cost.frequency() == RecurringFrequency.QUARTERLY || cost.frequency() == RecurringFrequency.YEARLY
                    ? 1 : properties.minObservedCycles();
            if (verification.state() == VerificationState.NO_CHARGE_OBSERVED && verification.coveredCycles() >= required) {
                cycles = verification.coveredCycles();
                observed = cost.amountMinor() * cycles;
                assumptions.add(cycles + " cycle(s) importe(s) sans prelevement correspondant.");
            } else {
                assumptions.add("Pas d'economie constatee : periode importee insuffisante, ou prelevement observe.");
            }
        }
        Map<String, Object> baseline = details("amountMinor", cost.amountMinor(), "frequency", cost.frequency(), "currency", cost.currency());
        return new SubscriptionSavings(subscriptionId, cost.currency(), annual, planned ? annual : null, confirmed ? annual : null,
                observed, cycles, assumptions, alternatives.current(), baseline);
    }

    Verification verify(UUID userId, Subscription subscription, Cancellation cancellation) {
        if (!FOLLOWED.contains(cancellation.status())) {
            return verification(VerificationState.NOT_APPLICABLE, null, 0, null, List.of(), false, "Aucune resiliation declaree a verifier.");
        }
        LocalDate reference = cancellation.providerEndDate() != null ? cancellation.providerEndDate() : cancellation.confirmedOn();
        if (reference == null) {
            return verification(VerificationState.AWAITING_END_DATE, null, 0, null, List.of(), false,
                    "Renseignez la date de fin annoncee pour permettre la verification.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        List<LedgerRow> rows = ledger.rows(subscription, repository.expenseRows(userId, today.minusYears(HISTORY_YEARS)));
        List<LedgerRow> charges = rows.stream().filter(row -> row.date().isAfter(reference.plusDays(properties.chargeGraceDays()))).toList();
        LocalDate lastImported = lastImported(userId, subscription);
        if (!charges.isEmpty()) {
            boolean changed = charges.stream().anyMatch(row -> cancellation.baselineAmountMinor() != null
                    && row.amountMinor() != cancellation.baselineAmountMinor());
            return verification(VerificationState.CHARGE_OBSERVED, reference, 0, lastImported, charges.stream().map(LedgerRow::id).toList(), changed,
                    "Un prelevement correspondant a ete observe apres la date de fin. A verifier aupres du fournisseur ou de votre banque.");
        }
        if (!today.isAfter(reference)) {
            return verification(VerificationState.WAITING_END_DATE, reference, 0, lastImported, List.of(), false, "La date de fin n'est pas encore atteinte.");
        }
        RecurringFrequency frequency = cancellation.baselineFrequency();
        if (frequency == RecurringFrequency.UNKNOWN) {
            return verification(VerificationState.INSUFFICIENT_DATA, reference, 0, lastImported, List.of(), false, "Frequence inconnue : aucun cycle attendu ne peut etre calcule.");
        }
        LocalDate anchor = rows.stream().map(LedgerRow::date).filter(date -> !date.isAfter(reference)).max(LocalDate::compareTo).orElse(reference);
        LocalDate expected = SubscriptionModels.next(anchor, frequency);
        while (expected.isBefore(reference)) expected = SubscriptionModels.next(expected, frequency);
        LocalDate firstExpected = expected;
        int covered = 0;
        while (lastImported != null && !expected.plusDays(properties.chargeGraceDays()).isAfter(lastImported)) {
            covered++;
            expected = SubscriptionModels.next(expected, frequency);
        }
        if (covered > 0) {
            return verification(VerificationState.NO_CHARGE_OBSERVED, reference, covered, lastImported, List.of(), false,
                    "Aucun prelevement observe sur " + covered + " cycle(s) importe(s). C'est un indice, pas une preuve de resiliation.");
        }
        if (firstExpected.plusDays(properties.chargeGraceDays()).isAfter(today)) {
            return verification(VerificationState.WAITING_NEXT_CYCLE, reference, 0, lastImported, List.of(), false, "Le prochain prelevement attendu n'est pas encore passe.");
        }
        return verification(VerificationState.INSUFFICIENT_DATA, reference, 0, lastImported, List.of(), false,
                "Vos imports ne couvrent pas le cycle attendu : impossible de conclure. Importez un releve plus recent.");
    }

    private LocalDate lastImported(UUID userId, Subscription subscription) {
        Map<UUID, LocalDate> dates = repository.lastImportedDates(userId);
        List<RecurringSeries> series = ledger.series(subscription);
        if (!series.isEmpty()) {
            return series.stream().map(item -> dates.get(item.getAccount().getId())).filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        }
        if (subscription.getAccount() != null) return dates.get(subscription.getAccount().getId());
        return dates.values().stream().max(LocalDate::compareTo).orElse(null);
    }

    private static Verification verification(VerificationState state, LocalDate reference, int cycles, LocalDate lastImported,
                                             List<UUID> charges, boolean changed, String explanation) {
        return new Verification(state, reference, cycles, lastImported, charges, changed, explanation);
    }

    private CancellationResponse response(Subscription subscription, Cancellation cancellation, Verification verification) {
        return new CancellationResponse(cancellation.id(), cancellation.subscriptionId(), cancellation.status(), cancellation.decidedAt(),
                cancellation.requestedOn(), cancellation.channel(), cancellation.requestReference(), subscription.getCommitmentEndDate(),
                cancellation.providerEndDate(), cancellation.confirmedOn(), cancellation.confirmationSource(), cancellation.confirmationReference(),
                cancellation.providerVerified(), cancellation.notes(), cancellation.version(), verification);
    }

    private Cancellation current(UUID subscriptionId) {
        return repository.latestCancellation(subscriptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cancellation not found"));
    }

    private static void requireTransition(CancellationStatus from, CancellationStatus to) {
        if (from != to && !from.canMoveTo(to)) {
            throw new ApiException(HttpStatus.CONFLICT, "Transition from " + from + " to " + to + " is not allowed");
        }
    }

    private void requireNotFuture(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now(clock.withZone(ZONE)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This date cannot be in the future");
        }
    }

    private Subscription require(UUID userId, UUID id) {
        return subscriptions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));
    }

    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1] == null ? null : pairs[index + 1].toString());
        }
        return values;
    }
}
