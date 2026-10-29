package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final CatalogSyncRepository repository;
    private final CatalogSyncCoordinator coordinator;
    private final Clock clock;

    public CatalogSyncJobService(DataSource dataSource, JdbcTemplate jdbc, CatalogSyncRepository repository,
                                 CatalogSyncCoordinator coordinator, Clock clock) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.repository = repository;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public CatalogSyncReport runNow() {
        Instant attemptedAt = clock.instant();
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection)) {
                repository.recordSkippedRun(repository.countConfiguredSources(), attemptedAt);
                return new CatalogSyncReport(null, attemptedAt, attemptedAt, "SKIPPED",
                        repository.countConfiguredSources(), 0, 0, 0, 0, 0, 0, 0, 0);
            }
            try {
                List<CatalogSyncSource> sources = repository.findEligibleSources();
                UUID runId = repository.startRun(sources.size());
                CatalogSyncRepository.SyncCounters counters = new CatalogSyncRepository.SyncCounters();
                Map<String, String> errors = new LinkedHashMap<>();
                String status = "SUCCEEDED";
                try {
                    counters = coordinator.collect(sources, errors);
                    if (!errors.isEmpty()) status = counters.sourcesProcessed == errors.size()
                            ? "FAILED" : "PARTIAL_FAILURE";
                } catch (RuntimeException exception) {
                    status = "FAILED";
                    counters.errorsCount++;
                    errors.put("run", exception.getClass().getSimpleName());
                    throw exception;
                } finally {
                    repository.finishRun(runId, status, counters, errors, clock.instant());
                }
                return report(runId);
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

    private CatalogSyncReport report(UUID id) {
        return jdbc.queryForObject("""
                SELECT id, started_at, finished_at, status, sources_configured, sources_processed,
                       pages_fetched, pages_unsupported, offers_discovered, offers_updated, offers_unchanged,
                       offers_rejected, errors_count
                FROM catalog_sync_runs WHERE id = ?
                """, (rs, row) -> new CatalogSyncReport(
                rs.getObject("id", UUID.class), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at").toInstant(), rs.getString("status"),
                rs.getInt("sources_configured"), rs.getInt("sources_processed"),
                rs.getInt("pages_fetched"), rs.getInt("pages_unsupported"),
                rs.getInt("offers_discovered"), rs.getInt("offers_updated"),
                rs.getInt("offers_unchanged"), rs.getInt("offers_rejected"), rs.getInt("errors_count")), id);
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
