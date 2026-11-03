package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.domain.CatalogSyncSourceReport;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogSyncJobService {
    private static final int LOCK_NAMESPACE = 1162895698;
    private static final int LOCK_KEY = 1280922457;

    private final DataSource dataSource;
    private final CatalogSyncRepository repository;
    private final CatalogSyncCoordinator coordinator;
    private final Clock clock;

    public CatalogSyncJobService(DataSource dataSource, CatalogSyncRepository repository,
                                 CatalogSyncCoordinator coordinator, Clock clock) {
        this.dataSource = dataSource;
        this.repository = repository;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public CatalogSyncReport runNow() {
        return runNow("manual");
    }

    public CatalogSyncReport runNow(String triggeredBy) {
        Instant attemptedAt = clock.instant();
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection)) {
                int configured = repository.countConfiguredSources();
                repository.recordSkippedRun(configured, attemptedAt);
                return new CatalogSyncReport(null, attemptedAt, attemptedAt, "SKIPPED",
                        configured, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            try {
                repository.failInterruptedRuns(attemptedAt);
                List<CatalogSyncSource> sources = repository.findEligibleSources();
                UUID runId = repository.startRun(sources.size(), triggeredBy);
                CatalogSyncRepository.SyncCounters counters = new CatalogSyncRepository.SyncCounters();
                Map<String, String> errors = new LinkedHashMap<>();
                String status = "SUCCESS";
                try {
                    counters = coordinator.collect(sources, runId, errors);
                    if (!errors.isEmpty()) status = counters.sourcesSucceeded > 0 ? "PARTIAL_SUCCESS" : "FAILED";
                } catch (RuntimeException exception) {
                    status = "FAILED";
                    counters.errorsCount++;
                    errors.put("run", exception.getClass().getSimpleName());
                    throw exception;
                } finally {
                    repository.finishRun(runId, status, counters, errors, clock.instant());
                }
                return repository.findRun(runId);
            } finally {
                unlock(lockConnection);
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not acquire or release catalog sync database lock", exception);
        }
    }

    public List<CatalogSyncReport> recentRuns(int limit) {
        int boundedLimit = Math.max(1, Math.min(100, limit));
        return repository.recentRuns(boundedLimit);
    }

    public boolean succeededSince(Instant since) {
        return repository.hasSuccessfulRunSince(since);
    }

    public List<CatalogSyncSourceReport> runSources(UUID runId) {
        return repository.findRunSources(runId);
    }

    private static boolean tryLock(Connection connection) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            statement.execute();
        }
    }
}
