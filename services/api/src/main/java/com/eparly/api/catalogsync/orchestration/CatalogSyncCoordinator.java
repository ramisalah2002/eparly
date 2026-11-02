package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.connector.CatalogConnectorRegistry;
import com.eparly.api.catalogsync.connector.CatalogSourceConnector;
import com.eparly.api.catalogsync.connector.CollectedSourceBatch;
import com.eparly.api.catalogsync.connector.CollectionBudget;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.fetch.RobotsPolicyService;
import com.eparly.api.catalogsync.fetch.SourceFetchException;
import com.eparly.api.catalogsync.normalization.CatalogDeduplicationService;
import com.eparly.api.catalogsync.normalization.NormalizedCatalogOffer;
import com.eparly.api.catalogsync.normalization.OfferNormalizationService;
import com.eparly.api.catalogsync.persistence.CatalogBusinessIngestionService;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CatalogSyncCoordinator {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncCoordinator.class);

    private final CatalogSyncRepository repository;
    private final CatalogBusinessIngestionService ingestion;
    private final CatalogConnectorRegistry connectors;
    private final RobotsPolicyService robotsPolicy;
    private final OfferNormalizationService normalization;
    private final CatalogDeduplicationService deduplication;
    private final CatalogSyncProperties properties;
    private final Clock clock;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public CatalogSyncCoordinator(CatalogSyncRepository repository, CatalogBusinessIngestionService ingestion,
                                  CatalogConnectorRegistry connectors,
                                  RobotsPolicyService robotsPolicy, OfferNormalizationService normalization,
                                  CatalogDeduplicationService deduplication,
                                  CatalogSyncProperties properties, Clock clock) {
        this.repository = repository;
        this.ingestion = ingestion;
        this.connectors = connectors;
        this.robotsPolicy = robotsPolicy;
        this.normalization = normalization;
        this.deduplication = deduplication;
        this.properties = properties;
        this.clock = clock;
        if (properties.getMaxExecutionDuration() == null
                || properties.getMaxExecutionDuration().isZero()
                || properties.getMaxExecutionDuration().isNegative()) {
            throw new IllegalArgumentException("Catalog sync maximum execution duration must be positive");
        }
    }

    public CatalogSyncRepository.SyncCounters collect(List<CatalogSyncSource> sources, UUID runId,
                                                       Map<String, String> sourceErrors) {
        robotsPolicy.beginRun();
        CatalogSyncRepository.SyncCounters counters = new CatalogSyncRepository.SyncCounters();
        if (sources.isEmpty()) return counters;
        CollectionBudget budget = new CollectionBudget(
                System.nanoTime() + properties.getMaxExecutionDuration().toNanos(), stopping::get);
        int parallelism = Math.max(1, Math.min(properties.getMaxParallelSources(), sources.size()));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<SourceOutcome>> outcomes = new ArrayList<>();
            for (CatalogSyncSource source : sources) {
                outcomes.add(executor.submit(() -> collectSource(source, runId, budget)));
            }
            for (int index = 0; index < outcomes.size(); index++) {
                merge(outcomes.get(index), sources.get(index), counters, sourceErrors);
            }
        } finally {
            executor.shutdown();
        }
        return counters;
    }

    @PreDestroy
    public void stopAcceptingWork() {
        stopping.set(true);
    }

    private static void merge(Future<SourceOutcome> future, CatalogSyncSource source,
                              CatalogSyncRepository.SyncCounters counters, Map<String, String> sourceErrors) {
        try {
            SourceOutcome outcome = future.get();
            counters.add(outcome.counters());
            if (outcome.deadlineReached()) {
                if (sourceErrors.putIfAbsent("run", "Maximum catalog sync execution duration reached") == null) {
                    counters.errorsCount++;
                }
            } else if (outcome.error() != null) {
                sourceErrors.put(source.id().toString(), outcome.error());
            }
        } catch (ExecutionException exception) {
            counters.errorsCount++;
            counters.sourcesFailed++;
            sourceErrors.put(source.id().toString(), safeError(exception.getCause()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while collecting catalog sources", exception);
        }
    }

    /** Toute erreur reste attachee a la source : les autres sources poursuivent leur collecte. */
    private SourceOutcome collectSource(CatalogSyncSource source, UUID runId, CollectionBudget budget) {
        CatalogSyncRepository.SyncCounters counters = new CatalogSyncRepository.SyncCounters();
        if (budget.stopping()) return new SourceOutcome(counters, null, false);
        if (budget.deadlineReached()) return new SourceOutcome(counters, null, true);

        Instant startedAt = clock.instant();
        try {
            if (repository.isCircuitOpen(source.id(), startedAt)) {
                counters.sourcesSkipped++;
                String message = "Source skipped: circuit breaker is open after repeated failures";
                repository.saveRunSource(runId, source.id(), "SKIPPED_CIRCUIT_OPEN", startedAt, clock.instant(),
                        counters, "CIRCUIT_OPEN", message);
                return new SourceOutcome(counters, message, false);
            }
            CatalogSourceConnector connector = connectors.connectorFor(source);
            CatalogSourceConnector.Readiness readiness = connector.readiness();
            if (!readiness.ready()) {
                counters.sourcesSkipped++;
                repository.saveRunSource(runId, source.id(), "SKIPPED_NOT_CONFIGURED", startedAt, clock.instant(),
                        counters, "NOT_CONFIGURED", readiness.reason());
                return new SourceOutcome(counters, null, false);
            }

            counters.sourcesProcessed++;
            if (!robotsAllowed(source)) {
                counters.sourcesSkipped++;
                repository.saveRunSource(runId, source.id(), "SKIPPED_ROBOTS", startedAt, clock.instant(),
                        counters, null, null);
                return new SourceOutcome(counters, null, false);
            }
            budget.ensureBeforeDeadline();
            CollectedSourceBatch batch = connector.collect(source, budget);
            persist(source, connector, batch, runId, counters, budget);
            repository.markSourceSuccess(source, batch.etag(), batch.lastModified(), clock.instant());
            counters.sourcesSucceeded++;
            if (batch.notModified()) counters.sourcesNotModified++;
            repository.saveRunSource(runId, source.id(), batch.notModified() ? "NOT_MODIFIED" : "SUCCESS",
                    startedAt, clock.instant(), counters, null, null);
            return new SourceOutcome(counters, null, false);
        } catch (CollectionBudget.RunDeadlineExceededException exception) {
            return failed(source, runId, counters, exception, startedAt, true);
        } catch (RuntimeException exception) {
            return failed(source, runId, counters, exception, startedAt, false);
        }
    }

    private SourceOutcome failed(CatalogSyncSource source, UUID runId, CatalogSyncRepository.SyncCounters counters,
                                 RuntimeException exception, Instant startedAt, boolean deadline) {
        counters.errorsCount += deadline ? 0 : 1;
        counters.sourcesFailed++;
        String safeMessage = safeError(exception);
        repository.markSourceError(source.id(), safeMessage, clock.instant());
        repository.saveRunSource(runId, source.id(), "FAILED", startedAt, clock.instant(), counters,
                errorCode(exception), safeMessage);
        log.warn("Catalog sync source {} failed: code={} message={}", source.id(), errorCode(exception), safeMessage);
        return new SourceOutcome(counters, safeMessage, deadline);
    }

    private boolean robotsAllowed(CatalogSyncSource source) {
        boolean allowed;
        try {
            allowed = robotsPolicy.isAllowed(source, source.sourceUrl());
        } catch (RuntimeException exception) {
            repository.updateSourceRobotsStatus(source.id(), "ERROR", clock.instant());
            throw exception;
        }
        repository.updateSourceRobotsStatus(source.id(), allowed ? "ALLOWED" : "DISALLOWED", clock.instant());
        return allowed;
    }

    private void persist(CatalogSyncSource source, CatalogSourceConnector connector, CollectedSourceBatch batch,
                         UUID runId, CatalogSyncRepository.SyncCounters counters, CollectionBudget budget) {
        counters.pagesFetched += batch.pagesFetched();
        for (String url : batch.unsupportedPages()) {
            NormalizedCatalogOffer page = normalization.unsupportedPage(url, source.officialDomain(), source.sector());
            repository.saveObservation(source, page, null, runId, clock.instant());
            counters.pagesUnsupported++;
        }

        List<String> offerDomains = connector.offerDomains(source);
        Set<String> seenKeys = new HashSet<>();
        boolean fullyPersisted = true;
        for (RawCatalogOffer raw : batch.offers()) {
            if (budget.stopping()) {
                fullyPersisted = false;
                break;
            }
            budget.ensureBeforeDeadline();
            counters.offersCollected++;
            NormalizedCatalogOffer normalized = normalization.normalize(raw, offerDomains);
            if (!seenKeys.add(normalized.externalKey())) {
                counters.offersDuplicates++;
                continue;
            }
            try {
                var identity = deduplication.identify(normalized, source.officialDomain());
                var saved = ingestion.ingest(source, normalized, identity, runId, clock.instant());
                record(saved.observation(), counters);
                counters.providersCreated += saved.providersCreated();
                counters.catalogOffersCreated += saved.offersCreated();
                counters.catalogPricesChanged += saved.pricesChanged();
                counters.catalogEvidenceAdded += saved.evidenceAdded();
            } catch (org.springframework.dao.DataAccessException exception) {
                // Une ligne inexploitable n'interrompt pas la source, mais rend sa lecture incomplete.
                counters.offersRejected++;
                counters.errorsCount++;
                fullyPersisted = false;
                log.warn("Catalog sync source {} could not persist an offer: {}", source.id(),
                        exception.getClass().getSimpleName());
            }
        }
        if (batch.complete() && fullyPersisted) {
            counters.offersNotReturned += repository.markOffersNotReturned(source.id(), runId, clock.instant());
        }
    }

    private static void record(CatalogSyncRepository.ObservationResult saved,
                               CatalogSyncRepository.SyncCounters counters) {
        if (!saved.newObservation()) {
            counters.offersUnchanged++;
        } else if (saved.rejected()) {
            counters.offersRejected++;
        } else if (saved.duplicate()) {
            counters.offersDuplicates++;
        } else if (saved.knownBefore()) {
            counters.offersUpdated++;
        } else {
            counters.offersDiscovered++;
        }
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof SourceFetchException fetch) return fetch.code().name();
        if (exception instanceof CollectionBudget.RunDeadlineExceededException) return "RUN_DEADLINE";
        if (exception instanceof UnsupportedOperationException) return "UNSUPPORTED_SOURCE";
        if (exception instanceof IllegalArgumentException) return "INVALID_SOURCE_OR_DATA";
        return "INTERNAL_ERROR";
    }

    private static String safeError(Throwable exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        message = message.replaceAll("https?://\\S+", "[source-url]");
        String result = exception.getClass().getSimpleName()
                + (message.isBlank() ? "" : ": " + message);
        return result.length() > 900 ? result.substring(0, 900) : result;
    }

    private record SourceOutcome(CatalogSyncRepository.SyncCounters counters, String error, boolean deadlineReached) {
    }
}
