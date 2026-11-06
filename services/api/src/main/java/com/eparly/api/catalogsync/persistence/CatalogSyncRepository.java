package com.eparly.api.catalogsync.persistence;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.domain.CatalogSyncSourceReport;
import com.eparly.api.catalogsync.normalization.CatalogDeduplicationService;
import com.eparly.api.catalogsync.normalization.CatalogPriceChangeDetector;
import com.eparly.api.catalogsync.normalization.NormalizedCatalogOffer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class CatalogSyncRepository {
    private static final String REPORT_COLUMNS = """
            id, started_at, finished_at, status, sources_configured, sources_processed,
            pages_fetched, pages_unsupported, offers_discovered, offers_updated, offers_unchanged,
            offers_rejected, errors_count, sources_failed, sources_skipped, sources_not_modified,
            offers_collected, offers_duplicates, offers_not_returned, providers_created,
            catalog_offers_created, catalog_prices_changed, catalog_evidence_added
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CatalogPriceChangeDetector changeDetector;
    private final CatalogSyncProperties properties;

    public CatalogSyncRepository(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                 CatalogPriceChangeDetector changeDetector, CatalogSyncProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.changeDetector = changeDetector;
        this.properties = properties;
    }

    public List<CatalogSyncSource> findEligibleSources() {
        return jdbc.query("""
                SELECT id, provider_id, source_name, official_domain, source_url, source_type,
                       sector, collection_enabled, permission_status, etag, last_modified,
                       extractor_config::text, connector_key
                FROM catalog_sync_sources
                WHERE collection_enabled = TRUE AND permission_status = 'AUTHORIZED'
                ORDER BY source_name, id
                """, this::sourceRow);
    }

    public int countConfiguredSources() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_sync_sources
                WHERE collection_enabled = TRUE AND permission_status = 'AUTHORIZED'
                """, Integer.class);
        return count == null ? 0 : count;
    }

    public UUID startRun(int configuredSources, String triggeredBy) {
        return jdbc.queryForObject("""
                INSERT INTO catalog_sync_runs(status, sources_configured, triggered_by)
                VALUES ('RUNNING', ?, ?)
                RETURNING id
                """, UUID.class, configuredSources, triggeredBy);
    }

    public void registerSchedulerInstance(UUID instanceId, long pid, String host, String cron, String zone,
                                          boolean businessIngestion, Instant nextRun, Instant now) {
        jdbc.update("""
                INSERT INTO catalog_sync_scheduler_instances
                    (instance_id, pid, host, cron, zone, business_ingestion, next_run_at, started_at, last_heartbeat_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, instanceId, pid, host, cron, zone, businessIngestion, Timestamp.from(nextRun),
                Timestamp.from(now), Timestamp.from(now));
    }

    public void heartbeatSchedulerInstance(UUID instanceId, Instant nextRun, Instant now) {
        jdbc.update("""
                UPDATE catalog_sync_scheduler_instances SET last_heartbeat_at = ?, next_run_at = ?
                WHERE instance_id = ?
                """, Timestamp.from(now), Timestamp.from(nextRun), instanceId);
    }

    public void stopSchedulerInstance(UUID instanceId, Instant now) {
        jdbc.update("UPDATE catalog_sync_scheduler_instances SET stopped_at = ? WHERE instance_id = ?",
                Timestamp.from(now), instanceId);
    }

    /** A n'appeler que sous le verrou de collecte : une ligne RUNNING provient alors d'une instance interrompue. */
    public int failInterruptedRuns(Instant now) {
        return jdbc.update("""
                UPDATE catalog_sync_runs
                SET status = 'FAILED', finished_at = ?,
                    summary = '{"reason":"interrupted before completion"}'::jsonb
                WHERE status = 'RUNNING'
                """, Timestamp.from(now));
    }

    public void finishRun(UUID id, String status, SyncCounters counters, Map<String, String> sourceErrors,
                          Instant finishedAt) {
        try {
            String summary = objectMapper.writeValueAsString(Map.of("sourceErrors", sourceErrors));
            jdbc.update("""
                    UPDATE catalog_sync_runs
                    SET finished_at = ?, status = ?, sources_processed = ?, pages_fetched = ?,
                        pages_unsupported = ?,
                        offers_discovered = ?, offers_updated = ?, offers_unchanged = ?,
                        offers_rejected = ?, errors_count = ?, sources_failed = ?, sources_skipped = ?,
                        sources_not_modified = ?, offers_collected = ?, offers_duplicates = ?,
                        offers_not_returned = ?, providers_created = ?, catalog_offers_created = ?,
                        catalog_prices_changed = ?, catalog_evidence_added = ?, summary = CAST(? AS jsonb)
                    WHERE id = ?
                    """, Timestamp.from(finishedAt), status, counters.sourcesProcessed, counters.pagesFetched,
                    counters.pagesUnsupported,
                    counters.offersDiscovered, counters.offersUpdated, counters.offersUnchanged,
                    counters.offersRejected, counters.errorsCount, counters.sourcesFailed, counters.sourcesSkipped,
                    counters.sourcesNotModified, counters.offersCollected, counters.offersDuplicates,
                    counters.offersNotReturned, counters.providersCreated, counters.catalogOffersCreated,
                    counters.catalogPricesChanged, counters.catalogEvidenceAdded, summary, id);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not record catalog synchronization summary", exception);
        }
    }

    public void recordSkippedRun(int configuredSources, Instant now) {
        jdbc.update("""
                INSERT INTO catalog_sync_runs(started_at, finished_at, status, sources_configured, summary)
                VALUES (?, ?, 'SKIPPED', ?, '{"reason":"another run holds the PostgreSQL advisory lock"}'::jsonb)
                """, Timestamp.from(now), Timestamp.from(now), configuredSources);
    }

    public void saveRunSource(UUID runId, UUID sourceId, String status, Instant startedAt, Instant finishedAt,
                              SyncCounters counters, String errorCode, String errorMessage) {
        jdbc.update("""
                INSERT INTO catalog_sync_run_sources
                    (run_id, source_id, status, started_at, finished_at, pages_fetched, offers_collected,
                     offers_discovered, offers_updated, offers_unchanged, offers_duplicates, offers_rejected,
                     offers_not_returned, error_code, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, sourceId, status, Timestamp.from(startedAt), Timestamp.from(finishedAt),
                counters.pagesFetched, counters.offersCollected, counters.offersDiscovered, counters.offersUpdated,
                counters.offersUnchanged, counters.offersDuplicates, counters.offersRejected,
                counters.offersNotReturned, errorCode, errorMessage);
    }

    public void updateSourceRobotsStatus(UUID sourceId, String status, Instant checkedAt) {
        jdbc.update("""
                UPDATE catalog_sync_sources
                SET robots_status = ?, last_checked_at = ?, last_error = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, Timestamp.from(checkedAt), sourceId);
    }

    public void markSourceSuccess(CatalogSyncSource source, String etag, String lastModified, Instant checkedAt) {
        jdbc.update("""
                UPDATE catalog_sync_sources
                SET last_checked_at = ?, last_success_at = ?, last_error = NULL,
                    etag = COALESCE(?, etag), last_modified = COALESCE(?, last_modified),
                    robots_status = 'ALLOWED', consecutive_failures = 0, circuit_open_until = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, Timestamp.from(checkedAt), Timestamp.from(checkedAt),
                etag, lastModified, source.id());
    }

    public void markSourceError(UUID sourceId, String message, Instant checkedAt) {
        int threshold = Math.max(1, properties.getCircuitBreakerFailureThreshold());
        Duration cooldown = properties.getCircuitBreakerCooldown();
        jdbc.update("""
                UPDATE catalog_sync_sources
                SET last_checked_at = ?, last_error = ?, consecutive_failures = consecutive_failures + 1,
                    circuit_open_until = CASE WHEN consecutive_failures + 1 >= ? THEN ? ELSE circuit_open_until END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, Timestamp.from(checkedAt), message.length() > 1000
                ? message.substring(0, 1000) : message, threshold,
                Timestamp.from(checkedAt.plus(cooldown)), sourceId);
    }

    public boolean isCircuitOpen(UUID sourceId, Instant now) {
        Boolean open = jdbc.queryForObject("""
                SELECT COALESCE(circuit_open_until > ?, FALSE) FROM catalog_sync_sources WHERE id = ?
                """, Boolean.class, Timestamp.from(now), sourceId);
        return Boolean.TRUE.equals(open);
    }

    /**
     * Note les offres absentes d'une lecture complete. Ce constat ne modifie ni offre, ni tarif,
     * ni candidat : seule une personne peut decider qu'une offre a disparu.
     *
     * @return nombre d'offres de la source actuellement non retournees
     */
    public int markOffersNotReturned(UUID sourceId, UUID runId, Instant now) {
        jdbc.update("""
                UPDATE catalog_source_offer_states
                SET missing_since = ?
                WHERE source_id = ? AND last_seen_run_id IS DISTINCT FROM ? AND missing_since IS NULL
                """, Timestamp.from(now), sourceId, runId);
        Integer missing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_source_offer_states
                WHERE source_id = ? AND missing_since IS NOT NULL
                """, Integer.class, sourceId);
        return missing == null ? 0 : missing;
    }

    @Transactional
    public ObservationResult saveObservation(CatalogSyncSource source, NormalizedCatalogOffer offer,
                                             CatalogDeduplicationService.OfferIdentity identity, UUID runId,
                                             Instant observedAt) {
        String normalized = writeJson(offer.asMap());
        String hash = CatalogDeduplicationService.sha256(normalized);
        Map<String, Object> content = readMap(normalized);
        Map<String, Object> pricing = changeDetector.snapshot(content);
        String fingerprint = CatalogDeduplicationService.sha256(writeJson(pricing));
        boolean rejected = offer.validationStatus() == CatalogObservationStatus.REJECTED;
        boolean unsupported = offer.validationStatus() == CatalogObservationStatus.UNSUPPORTED;

        UUID existingObservation = jdbc.query("""
                SELECT id FROM catalog_offer_observations
                WHERE source_id = ? AND offer_external_key = ? AND content_hash = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, source.id(), offer.externalKey(), hash);

        if (rejected || unsupported) {
            if (existingObservation != null) {
                touchObservation(existingObservation, runId, observedAt);
            } else {
                insertObservation(source, offer, null, runId, observedAt, normalized, hash, fingerprint);
            }
            touchState(source.id(), offer.externalKey(), runId, observedAt);
            return new ObservationResult(existingObservation == null, rejected, true, unsupported, false);
        }

        // L'upsert verrouille la ligne d'identite : deux sources paralleles sont serialisees pour une meme offre.
        UUID identityId = identity == null ? null : upsertIdentity(offer, identity, observedAt);
        UUID latestObservation = findLatestObservation(source.id(), offer.externalKey());
        if (existingObservation != null && existingObservation.equals(latestObservation)) {
            touchObservation(existingObservation, runId, observedAt);
            upsertState(source.id(), offer.externalKey(), identityId, existingObservation, runId, observedAt);
            return new ObservationResult(false, false, true, false, false);
        }

        Map<String, Object> previous = latestObservation == null ? null : readObservationContent(latestObservation);
        UUID observationId = existingObservation;
        if (observationId == null) {
            observationId = insertObservation(source, offer, identityId, runId, observedAt, normalized, hash, fingerprint);
        } else {
            // Retour a un contenu deja vu (A -> B -> A) : la ligne immuable est reutilisee.
            touchObservation(observationId, runId, observedAt);
        }
        upsertState(source.id(), offer.externalKey(), identityId, observationId, runId, observedAt);

        CatalogPriceChangeDetector.Change change = previous == null
                ? CatalogPriceChangeDetector.Change.NONE
                : changeDetector.detect(changeDetector.snapshot(previous), pricing);
        if (previous == null || change != CatalogPriceChangeDetector.Change.NONE) {
            insertPriceHistory(source.id(), offer, identityId, observationId, runId, previous, pricing,
                    previous == null ? "FIRST_OBSERVED" : change.name(), observedAt);
        }

        jdbc.update("""
                UPDATE catalog_offer_candidates candidate
                SET candidate_status = 'SUPERSEDED'
                FROM catalog_offer_observations previous
                WHERE candidate.latest_observation_id = previous.id
                  AND candidate.source_id = ? AND previous.offer_external_key = ?
                  AND candidate.candidate_status = 'NEEDS_REVIEW'
                """, source.id(), offer.externalKey());

        if (isDuplicateOfOtherSource(source.id(), identityId, fingerprint, previous == null)) {
            return new ObservationResult(true, false, true, false, true);
        }

        UUID existingOfferId = findExistingOfferId(source.providerId(), offer.name());
        boolean conflict = previous == null && reportedByOtherSource(source.id(), identityId);
        boolean knownBefore = existingOfferId != null || previous != null || conflict;
        String changeType;
        if (conflict) {
            changeType = "SOURCE_CONFLICT";
        } else if (!knownBefore) {
            changeType = "NEW_OFFER";
        } else if (previous != null) {
            changeType = change == CatalogPriceChangeDetector.Change.NONE ? "DETAILS_CHANGED" : change.name();
        } else {
            changeType = pricingChanged(existingOfferId, offer) ? "PRICE_CHANGED" : "DETAILS_CHANGED";
        }
        jdbc.update("""
                INSERT INTO catalog_offer_candidates
                    (source_id, provider_id, existing_offer_id, latest_observation_id, candidate_status, change_type,
                     identity_id, sector)
                VALUES (?, ?, ?, ?, 'NEEDS_REVIEW', ?, ?, ?)
                ON CONFLICT (latest_observation_id) WHERE candidate_status = 'NEEDS_REVIEW' DO NOTHING
                """, source.id(), source.providerId(), existingOfferId, observationId, changeType, identityId,
                offer.sector() == null ? null : offer.sector().name());
        return new ObservationResult(true, false, knownBefore, false, false);
    }

    public boolean hasSuccessfulRunSince(Instant since) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM catalog_sync_runs
                               WHERE status IN ('SUCCESS', 'PARTIAL_SUCCESS') AND started_at >= ?)
                """, Boolean.class, Timestamp.from(since));
        return Boolean.TRUE.equals(found);
    }

    public List<CatalogSyncReport> recentRuns(int limit) {
        return jdbc.query("SELECT " + REPORT_COLUMNS + " FROM catalog_sync_runs ORDER BY started_at DESC LIMIT ?",
                (rs, row) -> reportRow(rs), limit);
    }

    public CatalogSyncReport findRun(UUID id) {
        return jdbc.queryForObject("SELECT " + REPORT_COLUMNS + " FROM catalog_sync_runs WHERE id = ?",
                (rs, row) -> reportRow(rs), id);
    }

    public List<CatalogSyncSourceReport> findRunSources(UUID runId) {
        return jdbc.query("""
                SELECT run_source.*, source.source_name
                FROM catalog_sync_run_sources run_source
                JOIN catalog_sync_sources source ON source.id = run_source.source_id
                WHERE run_source.run_id = ? ORDER BY source.source_name, run_source.source_id
                """, (rs, row) -> new CatalogSyncSourceReport(rs.getObject("source_id", UUID.class),
                rs.getString("source_name"), rs.getString("status"), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at").toInstant(), rs.getInt("pages_fetched"),
                rs.getInt("offers_collected"), rs.getInt("offers_discovered"), rs.getInt("offers_updated"),
                rs.getInt("offers_unchanged"), rs.getInt("offers_duplicates"), rs.getInt("offers_rejected"),
                rs.getInt("offers_not_returned"), rs.getString("error_code"), rs.getString("error_message")), runId);
    }

    private UUID findLatestObservation(UUID sourceId, String externalKey) {
        UUID fromState = jdbc.query("""
                SELECT latest_observation_id FROM catalog_source_offer_states
                WHERE source_id = ? AND offer_external_key = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, sourceId, externalKey);
        if (fromState != null) return fromState;
        return jdbc.query("""
                SELECT id FROM catalog_offer_observations
                WHERE source_id = ? AND offer_external_key = ?
                  AND validation_status NOT IN ('REJECTED', 'UNSUPPORTED')
                ORDER BY last_observed_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, sourceId, externalKey);
    }

    private Map<String, Object> readObservationContent(UUID observationId) {
        return readMap(jdbc.queryForObject("SELECT normalized_data::text FROM catalog_offer_observations WHERE id = ?",
                String.class, observationId));
    }

    private UUID insertObservation(CatalogSyncSource source, NormalizedCatalogOffer offer, UUID identityId, UUID runId,
                                   Instant observedAt, String normalized, String hash, String fingerprint) {
        return jdbc.queryForObject("""
                INSERT INTO catalog_offer_observations
                    (source_id, provider_id, offer_external_key, canonical_url, observed_at, last_observed_at,
                     normalized_data, content_hash, confidence, validation_status, validation_reasons,
                     run_id, identity_id, sector, price_kind, pricing_fingerprint, comparability_status,
                     comparison_group, raw_payload)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb),
                        ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                RETURNING id
                """, UUID.class, source.id(), source.providerId(), offer.externalKey(), offer.canonicalUrl(),
                Timestamp.from(observedAt), Timestamp.from(observedAt),
                normalized, hash, offer.confidence(),
                offer.validationStatus().name(), writeJson(offer.validationReasons()),
                runId, identityId, offer.sector() == null ? null : offer.sector().name(),
                offer.priceKind() == null ? null : offer.priceKind().name(), fingerprint,
                offer.comparability() == null ? null : offer.comparability().status().name(),
                offer.comparability() == null ? null : offer.comparability().comparisonGroup(),
                boundedRawPayload(offer.rawPayload()));
    }

    private void touchObservation(UUID observationId, UUID runId, Instant observedAt) {
        jdbc.update("UPDATE catalog_offer_observations SET last_observed_at = ?, run_id = COALESCE(?, run_id) WHERE id = ?",
                Timestamp.from(observedAt), runId, observationId);
    }

    private void touchState(UUID sourceId, String externalKey, UUID runId, Instant observedAt) {
        jdbc.update("""
                UPDATE catalog_source_offer_states
                SET last_seen_at = ?, last_seen_run_id = ?, missing_since = NULL
                WHERE source_id = ? AND offer_external_key = ?
                """, Timestamp.from(observedAt), runId, sourceId, externalKey);
    }

    private void upsertState(UUID sourceId, String externalKey, UUID identityId, UUID observationId, UUID runId,
                             Instant observedAt) {
        jdbc.update("""
                INSERT INTO catalog_source_offer_states
                    (source_id, offer_external_key, identity_id, latest_observation_id, first_seen_at,
                     last_seen_at, last_seen_run_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, offer_external_key) DO UPDATE
                SET identity_id = EXCLUDED.identity_id, latest_observation_id = EXCLUDED.latest_observation_id,
                    last_seen_at = EXCLUDED.last_seen_at, last_seen_run_id = EXCLUDED.last_seen_run_id,
                    missing_since = NULL
                """, sourceId, externalKey, identityId, observationId, Timestamp.from(observedAt),
                Timestamp.from(observedAt), runId);
    }

    private UUID upsertIdentity(NormalizedCatalogOffer offer, CatalogDeduplicationService.OfferIdentity identity,
                                Instant observedAt) {
        return jdbc.queryForObject("""
                INSERT INTO catalog_offer_identities
                    (identity_key, sector, provider_key, plan_key, variant_key, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (identity_key) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
                RETURNING id
                """, UUID.class, identity.identityKey(), offer.sector().name(), truncate(identity.providerKey(), 300),
                truncate(identity.planKey(), 300), truncate(identity.variantKey(), 500),
                Timestamp.from(observedAt), Timestamp.from(observedAt));
    }

    /**
     * Doublon inter-sources : meme identite et meme tarif qu'une autre source. Pour une offre deja suivie,
     * seul un candidat ouvert compte, afin qu'un changement simultane ne produise qu'une seule revue.
     */
    private boolean isDuplicateOfOtherSource(UUID sourceId, UUID identityId, String fingerprint, boolean firstSeen) {
        if (identityId == null) return false;
        Boolean openCandidate = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM catalog_offer_candidates candidate
                    JOIN catalog_offer_observations observation ON observation.id = candidate.latest_observation_id
                    WHERE candidate.identity_id = ? AND candidate.source_id <> ?
                      AND candidate.candidate_status = 'NEEDS_REVIEW' AND observation.pricing_fingerprint = ?
                )
                """, Boolean.class, identityId, sourceId, fingerprint);
        if (Boolean.TRUE.equals(openCandidate) || !firstSeen) return Boolean.TRUE.equals(openCandidate);
        Boolean sameTariff = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM catalog_source_offer_states state
                    JOIN catalog_offer_observations observation ON observation.id = state.latest_observation_id
                    WHERE state.identity_id = ? AND state.source_id <> ? AND observation.pricing_fingerprint = ?
                )
                """, Boolean.class, identityId, sourceId, fingerprint);
        return Boolean.TRUE.equals(sameTariff);
    }

    private boolean reportedByOtherSource(UUID sourceId, UUID identityId) {
        if (identityId == null) return false;
        Boolean reported = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM catalog_source_offer_states WHERE identity_id = ? AND source_id <> ?
                )
                """, Boolean.class, identityId, sourceId);
        return Boolean.TRUE.equals(reported);
    }

    private void insertPriceHistory(UUID sourceId, NormalizedCatalogOffer offer, UUID identityId, UUID observationId,
                                    UUID runId, Map<String, Object> previous, Map<String, Object> pricing,
                                    String changeKind, Instant detectedAt) {
        Long oldAmount = previous == null || !(previous.get("amountMinor") instanceof Number amount)
                ? null : amount.longValue();
        jdbc.update("""
                INSERT INTO catalog_observed_price_history
                    (source_id, offer_external_key, identity_id, observation_id, run_id, change_kind,
                     old_amount_minor, new_amount_minor, old_snapshot, new_snapshot, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """, sourceId, offer.externalKey(), identityId, observationId, runId, changeKind, oldAmount,
                offer.amountMinor(), previous == null ? null : writeJson(changeDetector.snapshot(previous)),
                writeJson(pricing), Timestamp.from(detectedAt));
    }

    private String boundedRawPayload(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) return null;
        String json = writeJson(rawPayload);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= properties.getMaxRawPayloadBytes()) {
            return json;
        }
        Map<String, Object> truncated = new LinkedHashMap<>();
        truncated.put("truncated", true);
        truncated.put("sha256", CatalogDeduplicationService.sha256(json));
        return writeJson(truncated);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private UUID findExistingOfferId(UUID providerId, String name) {
        if (providerId == null || name == null) return null;
        return jdbc.query("""
                SELECT id FROM commercial_offers
                WHERE provider_id = ? AND lower(commercial_name) = lower(?)
                ORDER BY created_at LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, providerId, name);
    }

    private boolean pricingChanged(UUID offerId, NormalizedCatalogOffer observed) {
        return jdbc.query("""
                SELECT amount_minor, currency, billing_period
                FROM offer_pricing WHERE offer_id = ?
                """, rs -> {
            if (!rs.next()) return true;
            long amount = rs.getLong("amount_minor");
            Long currentAmount = rs.wasNull() ? null : amount;
            String currency = rs.getString("currency");
            String period = rs.getString("billing_period");
            return !java.util.Objects.equals(currentAmount, observed.amountMinor())
                    || !java.util.Objects.equals(currency, observed.currency())
                    || !java.util.Objects.equals(period, observed.billingPeriod() == null
                    ? null : observed.billingPeriod().name());
        }, offerId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not serialize catalog observation metadata", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not read persisted catalog observation", exception);
        }
    }

    private CatalogSyncSource sourceRow(ResultSet rs, int row) throws SQLException {
        return new CatalogSyncSource(rs.getObject("id", UUID.class),
                rs.getObject("provider_id", UUID.class), rs.getString("source_name"),
                rs.getString("official_domain"), rs.getString("source_url"),
                CatalogSourceType.valueOf(rs.getString("source_type")),
                com.eparly.api.offer.domain.OfferSector.valueOf(rs.getString("sector")),
                rs.getBoolean("collection_enabled"), rs.getString("permission_status"),
                rs.getString("etag"), rs.getString("last_modified"), rs.getString("extractor_config"),
                rs.getString("connector_key"));
    }

    private static CatalogSyncReport reportRow(ResultSet rs) throws SQLException {
        Instant startedAt = rs.getTimestamp("started_at").toInstant();
        Instant finishedAt = rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant();
        return new CatalogSyncReport(rs.getObject("id", UUID.class), startedAt, finishedAt,
                rs.getString("status"), rs.getInt("sources_configured"), rs.getInt("sources_processed"),
                rs.getInt("pages_fetched"), rs.getInt("pages_unsupported"),
                rs.getInt("offers_discovered"), rs.getInt("offers_updated"),
                rs.getInt("offers_unchanged"), rs.getInt("offers_rejected"), rs.getInt("errors_count"),
                finishedAt == null ? null : Math.max(0, Duration.between(startedAt, finishedAt).toMillis()),
                rs.getInt("sources_failed"), rs.getInt("sources_skipped"), rs.getInt("sources_not_modified"),
                rs.getInt("offers_collected"), rs.getInt("offers_duplicates"), rs.getInt("offers_not_returned"),
                rs.getInt("providers_created"), rs.getInt("catalog_offers_created"),
                rs.getInt("catalog_prices_changed"), rs.getInt("catalog_evidence_added"));
    }

    public static final class SyncCounters {
        public int sourcesProcessed;
        public int sourcesSucceeded;
        public int sourcesFailed;
        public int sourcesSkipped;
        public int sourcesNotModified;
        public int pagesFetched;
        public int pagesUnsupported;
        public int offersCollected;
        public int offersDiscovered;
        public int offersUpdated;
        public int offersUnchanged;
        public int offersDuplicates;
        public int offersRejected;
        public int offersNotReturned;
        public int errorsCount;
        public int providersCreated;
        public int catalogOffersCreated;
        public int catalogPricesChanged;
        public int catalogEvidenceAdded;

        public void add(SyncCounters other) {
            sourcesProcessed += other.sourcesProcessed;
            sourcesSucceeded += other.sourcesSucceeded;
            sourcesFailed += other.sourcesFailed;
            sourcesSkipped += other.sourcesSkipped;
            sourcesNotModified += other.sourcesNotModified;
            pagesFetched += other.pagesFetched;
            pagesUnsupported += other.pagesUnsupported;
            offersCollected += other.offersCollected;
            offersDiscovered += other.offersDiscovered;
            offersUpdated += other.offersUpdated;
            offersUnchanged += other.offersUnchanged;
            offersDuplicates += other.offersDuplicates;
            offersRejected += other.offersRejected;
            offersNotReturned += other.offersNotReturned;
            errorsCount += other.errorsCount;
            providersCreated += other.providersCreated;
            catalogOffersCreated += other.catalogOffersCreated;
            catalogPricesChanged += other.catalogPricesChanged;
            catalogEvidenceAdded += other.catalogEvidenceAdded;
        }
    }

    public record ObservationResult(boolean newObservation, boolean rejected, boolean knownBefore,
                                    boolean unsupported, boolean duplicate) {
    }
}
