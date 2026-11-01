package com.eparly.api.catalogsync.persistence;

import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.normalization.NormalizedCatalogOffer;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class CatalogSyncRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CatalogSyncRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<CatalogSyncSource> findEligibleSources() {
        return jdbc.query("""
                SELECT id, provider_id, source_name, official_domain, source_url, source_type,
                       sector, collection_enabled, permission_status, etag, last_modified,
                       extractor_config::text
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

    public UUID startRun(int configuredSources) {
        return jdbc.queryForObject("""
                INSERT INTO catalog_sync_runs(status, sources_configured)
                VALUES ('RUNNING', ?)
                RETURNING id
                """, UUID.class, configuredSources);
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
                        offers_rejected = ?, errors_count = ?, summary = CAST(? AS jsonb)
                    WHERE id = ?
                    """, java.sql.Timestamp.from(finishedAt), status, counters.sourcesProcessed, counters.pagesFetched,
                    counters.pagesUnsupported,
                    counters.offersDiscovered, counters.offersUpdated, counters.offersUnchanged,
                    counters.offersRejected, counters.errorsCount, summary, id);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize catalog synchronization summary", exception);
        }
    }

    public void recordSkippedRun(int configuredSources, Instant now) {
        jdbc.update("""
                INSERT INTO catalog_sync_runs(started_at, finished_at, status, sources_configured, summary)
                VALUES (?, ?, 'SKIPPED', ?, '{"reason":"another run holds the PostgreSQL advisory lock"}'::jsonb)
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), configuredSources);
    }

    public void updateSourceRobotsStatus(UUID sourceId, String status, Instant checkedAt) {
        jdbc.update("""
                UPDATE catalog_sync_sources
                SET robots_status = ?, last_checked_at = ?, last_error = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, java.sql.Timestamp.from(checkedAt), sourceId);
    }

    public void markSourceSuccess(CatalogSyncSource source, String etag, String lastModified, Instant checkedAt) {
        jdbc.update("""
                UPDATE catalog_sync_sources
                SET last_checked_at = ?, last_success_at = ?, last_error = NULL,
                    etag = COALESCE(?, etag), last_modified = COALESCE(?, last_modified),
                    robots_status = 'ALLOWED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, java.sql.Timestamp.from(checkedAt), java.sql.Timestamp.from(checkedAt),
                etag, lastModified, source.id());
    }

    public void markSourceError(UUID sourceId, String message, Instant checkedAt) {
        jdbc.update("""
                UPDATE catalog_sync_sources
                SET last_checked_at = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, java.sql.Timestamp.from(checkedAt), message.length() > 1000
                ? message.substring(0, 1000) : message, sourceId);
    }

    @Transactional
    public ObservationResult saveObservation(CatalogSyncSource source, NormalizedCatalogOffer offer,
                                             Instant observedAt) {
        String normalized;
        String hash;
        try {
            normalized = objectMapper.writeValueAsString(offer.asMap());
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not fingerprint normalized catalog observation", exception);
        }

        List<UUID> existing = jdbc.query("""
                SELECT id FROM catalog_offer_observations
                WHERE source_id = ? AND offer_external_key = ? AND content_hash = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), source.id(), offer.externalKey(), hash);
        if (!existing.isEmpty()) {
            jdbc.update("UPDATE catalog_offer_observations SET last_observed_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(observedAt), existing.get(0));
            return new ObservationResult(false, offer.validationStatus() == CatalogObservationStatus.REJECTED, true,
                    offer.validationStatus() == CatalogObservationStatus.UNSUPPORTED);
        }

        UUID existingOfferId = findExistingOfferId(source.providerId(), offer.name());
        String previousObservation = jdbc.query("""
                SELECT normalized_data::text FROM catalog_offer_observations
                WHERE source_id = ? AND offer_external_key = ?
                ORDER BY observed_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, source.id(), offer.externalKey());
        UUID observationId = jdbc.queryForObject("""
                INSERT INTO catalog_offer_observations
                    (source_id, provider_id, offer_external_key, canonical_url, observed_at, last_observed_at,
                     normalized_data, content_hash, confidence, validation_status, validation_reasons)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb))
                RETURNING id
                """, UUID.class, source.id(), source.providerId(), offer.externalKey(), offer.canonicalUrl(),
                java.sql.Timestamp.from(observedAt), java.sql.Timestamp.from(observedAt),
                normalized, hash, offer.confidence(),
                offer.validationStatus().name(), writeJson(offer.validationReasons()));

        jdbc.update("""
                UPDATE catalog_offer_candidates candidate
                SET candidate_status = 'SUPERSEDED'
                FROM catalog_offer_observations previous
                WHERE candidate.latest_observation_id = previous.id
                  AND candidate.source_id = ? AND previous.offer_external_key = ?
                  AND candidate.candidate_status = 'NEEDS_REVIEW'
                """, source.id(), offer.externalKey());

        if (offer.validationStatus() == CatalogObservationStatus.REJECTED
                || offer.validationStatus() == CatalogObservationStatus.UNSUPPORTED) {
            return new ObservationResult(true,
                    offer.validationStatus() == CatalogObservationStatus.REJECTED, true,
                    offer.validationStatus() == CatalogObservationStatus.UNSUPPORTED);
        }
        boolean knownBefore = existingOfferId != null || previousObservation != null;
        boolean changedPrice = previousObservation != null
                ? pricingChanged(previousObservation, offer)
                : existingOfferId != null && pricingChanged(existingOfferId, offer);
        String changeType = !knownBefore ? "NEW_OFFER"
                : changedPrice ? "PRICE_CHANGED" : "DETAILS_CHANGED";
        jdbc.update("""
                INSERT INTO catalog_offer_candidates
                    (source_id, provider_id, existing_offer_id, latest_observation_id, candidate_status, change_type)
                VALUES (?, ?, ?, ?, 'NEEDS_REVIEW', ?)
                ON CONFLICT (latest_observation_id) DO NOTHING
                """, source.id(), source.providerId(), existingOfferId, observationId, changeType);
        return new ObservationResult(true, false, knownBefore, false);
    }

    public List<CatalogSyncReport> recentRuns(int limit) {
        return jdbc.query("""
                SELECT id, started_at, finished_at, status, sources_configured, sources_processed,
                       pages_fetched, pages_unsupported, offers_discovered, offers_updated, offers_unchanged,
                       offers_rejected, errors_count
                FROM catalog_sync_runs ORDER BY started_at DESC LIMIT ?
                """, (rs, row) -> reportRow(rs), limit);
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

    private boolean pricingChanged(String previousJson, NormalizedCatalogOffer observed) {
        try {
            var previous = objectMapper.readTree(previousJson);
            String amount = previous.path("amountMinor").asText("");
            String currency = previous.path("currency").asText("");
            String period = previous.path("billingPeriod").asText("");
            return !amount.equals(observed.amountMinor() == null ? "" : observed.amountMinor().toString())
                    || !currency.equals(observed.currency() == null ? "" : observed.currency())
                    || !period.equals(observed.billingPeriod() == null ? "" : observed.billingPeriod().name());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not compare successive catalog observations", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize catalog observation metadata", exception);
        }
    }

    private CatalogSyncSource sourceRow(ResultSet rs, int row) throws SQLException {
        return new CatalogSyncSource(rs.getObject("id", UUID.class),
                rs.getObject("provider_id", UUID.class), rs.getString("source_name"),
                rs.getString("official_domain"), rs.getString("source_url"),
                CatalogSourceType.valueOf(rs.getString("source_type")),
                com.eparly.api.offer.domain.OfferSector.valueOf(rs.getString("sector")),
                rs.getBoolean("collection_enabled"), rs.getString("permission_status"),
                rs.getString("etag"), rs.getString("last_modified"), rs.getString("extractor_config"));
    }

    private static CatalogSyncReport reportRow(ResultSet rs) throws SQLException {
        return new CatalogSyncReport(rs.getObject("id", UUID.class), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                rs.getString("status"), rs.getInt("sources_configured"), rs.getInt("sources_processed"),
                rs.getInt("pages_fetched"), rs.getInt("pages_unsupported"),
                rs.getInt("offers_discovered"), rs.getInt("offers_updated"),
                rs.getInt("offers_unchanged"), rs.getInt("offers_rejected"), rs.getInt("errors_count"));
    }

    public static final class SyncCounters {
        public int sourcesProcessed;
        public int pagesFetched;
        public int pagesUnsupported;
        public int offersDiscovered;
        public int offersUpdated;
        public int offersUnchanged;
        public int offersRejected;
        public int errorsCount;
    }

    public record ObservationResult(boolean newObservation, boolean rejected, boolean knownBefore,
                                    boolean unsupported) {
    }
}
