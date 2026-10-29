package com.eparly.api.savings.action;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.domain.SavingsActionHistory;
import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.action.dto.CreateSavingsActionRequest;
import com.eparly.api.savings.action.dto.SavingsActionResponse;
import com.eparly.api.savings.action.dto.SavingsEstimateSnapshot;
import com.eparly.api.savings.action.dto.SavingsPlanSummaryResponse;
import com.eparly.api.savings.action.dto.UpdateSavingsActionStatusRequest;
import com.eparly.api.savings.action.repository.SavingsActionHistoryRepository;
import com.eparly.api.savings.action.repository.SavingsActionRepository;
import com.eparly.api.savings.action.service.SavingsActionService;
import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.savings.domain.SavingsStatus;
import com.eparly.api.savings.dto.SavingsEstimateRequest;
import com.eparly.api.savings.dto.SavingsEstimateResponse;
import com.eparly.api.savings.service.SavingsEstimationService;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavingsActionServiceUnitTest {

    private SubscriptionRepository subscriptionRepository;
    private CommercialOfferRepository offerRepository;
    private OfferPricingRepository pricingRepository;
    private OfferEvidenceRepository offerEvidenceRepository;
    private SavingsEstimationService savingsEstimationService;
    private SavingsActionRepository actionRepository;
    private SavingsActionHistoryRepository historyRepository;

    private Instant fixedInstant;
    private Clock fixedClock;
    private SavingsActionService service;

    private UUID userId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        offerRepository = mock(CommercialOfferRepository.class);
        pricingRepository = mock(OfferPricingRepository.class);
        offerEvidenceRepository = mock(OfferEvidenceRepository.class);
        savingsEstimationService = mock(SavingsEstimationService.class);
        actionRepository = mock(SavingsActionRepository.class);
        historyRepository = mock(SavingsActionHistoryRepository.class);

        fixedInstant = Instant.parse("2026-10-29T10:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        service = new SavingsActionService(
                subscriptionRepository,
                offerRepository,
                pricingRepository,
                offerEvidenceRepository,
                savingsEstimationService,
                null,
                List.of(),
                actionRepository,
                historyRepository,
                fixedClock
        );

        userId = UUID.randomUUID();
        jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());
    }

    /* ---------- A. Transitions d'état autorisées et interdites ---------- */

    @Test
    @DisplayName("Transition autorisée : PLANNED -> COMMITTED")
    void transitionPlannedToCommitted() {
        SavingsAction action = createActionWithStatus(ActionStatus.PLANNED);
        when(actionRepository.findByIdAndUserId(action.getId(), userId)).thenReturn(Optional.of(action));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsActionResponse res = service.updateStatus(jwt, action.getId(),
                new UpdateSavingsActionStatusRequest(ActionStatus.COMMITTED, "En cours"));

        assertThat(res.status()).isEqualTo(ActionStatus.COMMITTED);
        assertThat(action.getCommittedAt()).isEqualTo(fixedInstant);

        ArgumentCaptor<SavingsActionHistory> captor = ArgumentCaptor.forClass(SavingsActionHistory.class);
        verify(historyRepository).save(captor.capture());
        SavingsActionHistory history = captor.getValue();
        assertThat(history.getPreviousStatus()).isEqualTo(ActionStatus.PLANNED);
        assertThat(history.getNewStatus()).isEqualTo(ActionStatus.COMMITTED);
        assertThat(history.getCreatedAt()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("Transition autorisée : COMMITTED -> DECLARED")
    void transitionCommittedToDeclared() {
        SavingsAction action = createActionWithStatus(ActionStatus.COMMITTED);
        when(actionRepository.findByIdAndUserId(action.getId(), userId)).thenReturn(Optional.of(action));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsActionResponse res = service.updateStatus(jwt, action.getId(),
                new UpdateSavingsActionStatusRequest(ActionStatus.DECLARED, "Fait"));

        assertThat(res.status()).isEqualTo(ActionStatus.DECLARED);
        assertThat(action.getDeclaredAt()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("Transition autorisée : DECLARED -> CANCELED")
    void transitionDeclaredToCanceled() {
        SavingsAction action = createActionWithStatus(ActionStatus.DECLARED);
        when(actionRepository.findByIdAndUserId(action.getId(), userId)).thenReturn(Optional.of(action));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsActionResponse res = service.updateStatus(jwt, action.getId(),
                new UpdateSavingsActionStatusRequest(ActionStatus.CANCELED, "Annulé"));

        assertThat(res.status()).isEqualTo(ActionStatus.CANCELED);
        assertThat(action.getCanceledAt()).isEqualTo(fixedInstant);
    }

    @ParameterizedTest(name = "Transition interdite de {0} vers {1}")
    @CsvSource({
            "PLANNED, DECLARED",
            "COMMITTED, PLANNED",
            "DECLARED, PLANNED",
            "DECLARED, COMMITTED",
            "CANCELED, PLANNED",
            "CANCELED, COMMITTED",
            "CANCELED, DECLARED"
    })
    void forbiddenTransitionsThrowBadRequest(ActionStatus from, ActionStatus to) {
        SavingsAction action = createActionWithStatus(from);
        when(actionRepository.findByIdAndUserId(action.getId(), userId)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.updateStatus(jwt, action.getId(),
                new UpdateSavingsActionStatusRequest(to, null)))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).getStatus() == HttpStatus.BAD_REQUEST);

        verify(actionRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotence : renvoyer le même statut ne modifie pas les dates et ne crée pas d'historique")
    void idempotentStatusUpdate() {
        SavingsAction action = createActionWithStatus(ActionStatus.COMMITTED);
        Instant originalCommittedAt = fixedInstant.minusSeconds(3600);
        action.setCommittedAt(originalCommittedAt);

        when(actionRepository.findByIdAndUserId(action.getId(), userId)).thenReturn(Optional.of(action));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsActionResponse res = service.updateStatus(jwt, action.getId(),
                new UpdateSavingsActionStatusRequest(ActionStatus.COMMITTED, "Nouvelle note"));

        assertThat(res.status()).isEqualTo(ActionStatus.COMMITTED);
        assertThat(action.getCommittedAt()).isEqualTo(originalCommittedAt); // Non écrasé
        assertThat(action.getNotes()).isEqualTo("Nouvelle note");

        // Aucun historique enregistré pour une mise à jour idempotente
        verify(historyRepository, never()).save(any());
    }

    /* ---------- B. Dates et détection d'obsolescence (Horloge) ---------- */

    @Test
    @DisplayName("Horloge fixe : vérification de l'obsolescence de l'estimation (> 30 jours)")
    void staleEstimateDetection() {
        SavingsAction freshAction = createActionWithStatus(ActionStatus.PLANNED);
        // Offre vérifiée il y a 10 jours (< 30 jours)
        freshAction.setEstimateSnapshot(createSnapshot(fixedInstant.minusSeconds(86400 * 10), 7200L, "EUR", 12));

        SavingsAction staleAction = createActionWithStatus(ActionStatus.PLANNED);
        // Offre vérifiée il y a 35 jours (> 30 jours)
        staleAction.setEstimateSnapshot(createSnapshot(fixedInstant.minusSeconds(86400 * 35), 7200L, "EUR", 12));

        when(actionRepository.findByIdAndUserId(freshAction.getId(), userId)).thenReturn(Optional.of(freshAction));
        when(actionRepository.findByIdAndUserId(staleAction.getId(), userId)).thenReturn(Optional.of(staleAction));

        SavingsActionResponse freshRes = service.get(jwt, freshAction.getId());
        assertThat(freshRes.estimateMayBeStale()).isFalse();

        SavingsActionResponse staleRes = service.get(jwt, staleAction.getId());
        assertThat(staleRes.estimateMayBeStale()).isTrue();
    }

    /* ---------- C. Résumé financier : agrégation, devises, horizons et pas d'observé fictif ---------- */

    @Test
    @DisplayName("Résumé financier : séparation des montants, exclusion de CANCELED, absence d'observé fictif")
    void summaryCalculations() {
        SavingsAction aPlanned = createActionWithStatus(ActionStatus.PLANNED);
        aPlanned.setEstimateSnapshot(createSnapshot(fixedInstant, 5000L, "EUR", 12));

        SavingsAction aCommitted = createActionWithStatus(ActionStatus.COMMITTED);
        aCommitted.setEstimateSnapshot(createSnapshot(fixedInstant, 3000L, "EUR", 12));

        SavingsAction aDeclared = createActionWithStatus(ActionStatus.DECLARED);
        aDeclared.setEstimateSnapshot(createSnapshot(fixedInstant, 2000L, "EUR", 12));

        SavingsAction aCanceled = createActionWithStatus(ActionStatus.CANCELED);
        aCanceled.setEstimateSnapshot(createSnapshot(fixedInstant, 8000L, "EUR", 12));

        when(actionRepository.countByUserId(userId)).thenReturn(4L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.PLANNED)).thenReturn(1L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.COMMITTED)).thenReturn(1L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.DECLARED)).thenReturn(1L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.CANCELED)).thenReturn(1L);

        when(actionRepository.findAllByUserIdFiltered(eq(userId), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aPlanned, aCommitted, aDeclared, aCanceled)));

        SavingsPlanSummaryResponse summary = service.summary(jwt);

        assertThat(summary.actionsCount()).isEqualTo(4);
        assertThat(summary.plannedCount()).isEqualTo(1);
        assertThat(summary.committedCount()).isEqualTo(1);
        assertThat(summary.declaredCount()).isEqualTo(1);
        assertThat(summary.canceledCount()).isEqualTo(1);

        // Potentiel total : 5000 + 3000 + 2000 = 10000 (8000 de CANCELED est exclu !)
        assertThat(summary.potentialSavingsMinor()).isEqualTo(10000L);
        assertThat(summary.committedPotentialSavingsMinor()).isEqualTo(3000L);
        assertThat(summary.declaredPotentialSavingsMinor()).isEqualTo(2000L);

        // observedSavingsMinor strictement null à l'étape 11A
        assertThat(summary.observedSavingsMinor()).isNull();
        assertThat(summary.currency()).isEqualTo("EUR");
        assertThat(summary.horizonMonths()).isEqualTo(12);
    }

    @Test
    @DisplayName("Résumé financier : isolation stricte des devises")
    void summaryDoesNotMixCurrencies() {
        SavingsAction aEur = createActionWithStatus(ActionStatus.PLANNED);
        aEur.setEstimateSnapshot(createSnapshot(fixedInstant, 5000L, "EUR", 12));

        SavingsAction aUsd = createActionWithStatus(ActionStatus.PLANNED);
        aUsd.setEstimateSnapshot(createSnapshot(fixedInstant, 7000L, "USD", 12));

        when(actionRepository.countByUserId(userId)).thenReturn(2L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.PLANNED)).thenReturn(2L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.COMMITTED)).thenReturn(0L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.DECLARED)).thenReturn(0L);
        when(actionRepository.countByUserIdAndStatus(userId, ActionStatus.CANCELED)).thenReturn(0L);

        when(actionRepository.findAllByUserIdFiltered(eq(userId), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aEur, aUsd)));

        SavingsPlanSummaryResponse summary = service.summary(jwt);

        // Devise de référence : EUR (la première action trouvée)
        assertThat(summary.currency()).isEqualTo("EUR");
        // Les montants en USD ne sont pas additionnés aux montants en EUR
        assertThat(summary.potentialSavingsMinor()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Résumé financier : gestion sûre d'une liste vide")
    void summaryHandlesEmptyList() {
        when(actionRepository.countByUserId(userId)).thenReturn(0L);
        when(actionRepository.countByUserIdAndStatus(any(), any())).thenReturn(0L);
        when(actionRepository.findAllByUserIdFiltered(eq(userId), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        SavingsPlanSummaryResponse summary = service.summary(jwt);

        assertThat(summary.actionsCount()).isZero();
        assertThat(summary.potentialSavingsMinor()).isZero();
        assertThat(summary.committedPotentialSavingsMinor()).isZero();
        assertThat(summary.declaredPotentialSavingsMinor()).isZero();
        assertThat(summary.observedSavingsMinor()).isNull();
        assertThat(summary.currency()).isEqualTo("EUR");
        assertThat(summary.horizonMonths()).isEqualTo(12);
    }

    /* ---------- D. Validation et gestion des erreurs ---------- */

    @Test
    @DisplayName("Création refusée si la requête est null")
    void createThrowsWhenRequestIsNull() {
        assertThatThrownBy(() -> service.create(jwt, null))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).getStatus() == HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Création refusée si l'abonnement n'appartient pas à l'utilisateur (404 neutre)")
    void createThrows404WhenSubscriptionNotFound() {
        UUID subId = UUID.randomUUID();
        when(subscriptionRepository.findByIdAndUserId(subId, userId)).thenReturn(Optional.empty());

        CreateSavingsActionRequest req = new CreateSavingsActionRequest(
                subId, UUID.randomUUID(), OfferSector.STREAMING, 12, Map.of(), 0L, 0L, null);

        assertThatThrownBy(() -> service.create(jwt, req))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).getStatus() == HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Création refusée si l'estimation ne dégage aucune économie positive")
    void createThrowsWhenEstimatedSavingsZeroOrNegative() {
        Subscription sub = createSubscriptionMock();
        when(subscriptionRepository.findByIdAndUserId(sub.getId(), userId)).thenReturn(Optional.of(sub));
        when(actionRepository.findActiveActionsBySubscriptionAndOffer(any(), any())).thenReturn(Collections.emptyList());

        SavingsEstimateResponse zeroEstimate = mock(SavingsEstimateResponse.class);
        when(zeroEstimate.estimatedNetSavingsMinor()).thenReturn(0L);
        when(savingsEstimationService.estimate(eq(jwt), any())).thenReturn(zeroEstimate);

        CreateSavingsActionRequest req = new CreateSavingsActionRequest(
                sub.getId(), UUID.randomUUID(), OfferSector.STREAMING, 12, Map.of(), 0L, 0L, null);

        assertThatThrownBy(() -> service.create(jwt, req))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).getStatus() == HttpStatus.BAD_REQUEST);
    }

    /* ---------- Helpers internes ---------- */

    private SavingsAction createActionWithStatus(ActionStatus status) {
        SavingsAction a = new SavingsAction();
        a.setId(UUID.randomUUID());
        a.setStatus(status);
        a.setVerificationStatus(VerificationStatus.NOT_VERIFIED);
        a.setHorizonMonths(12);
        a.setCreatedAt(fixedInstant);
        a.setUpdatedAt(fixedInstant);

        User u = new User();
        u.setId(userId);
        a.setUser(u);

        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setName("Test Sub");
        sub.setProvider("Test Provider");
        sub.setUser(u);
        a.setSubscription(sub);

        CommercialOffer offer = new CommercialOffer();
        offer.setId(UUID.randomUUID());
        offer.setCommercialName("Test Offer");
        offer.setSector(OfferSector.STREAMING);
        OfferProvider p = new OfferProvider();
        p.setName("Provider Alt");
        offer.setProvider(p);
        a.setAlternativeOffer(offer);

        return a;
    }

    private Subscription createSubscriptionMock() {
        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setName("Sub");
        sub.setProvider("Prov");
        sub.setCurrency("EUR");
        User u = new User();
        u.setId(userId);
        sub.setUser(u);
        return sub;
    }

    private SavingsEstimateSnapshot createSnapshot(Instant verifiedAt, Long potentialSavings, String currency, Integer horizon) {
        return new SavingsEstimateSnapshot(
                "1.0.0",
                horizon,
                currency,
                15000L,
                CostSource.USER_DECLARED,
                10000L,
                0L,
                potentialSavings,
                SavingsStatus.SAVINGS_POSSIBLE,
                verifiedAt,
                fixedInstant,
                List.of("Hypothèse 1"),
                List.of(),
                "https://official.test"
        );
    }
}
