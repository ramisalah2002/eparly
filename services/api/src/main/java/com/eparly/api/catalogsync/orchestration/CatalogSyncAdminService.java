package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.catalogsync.api.CatalogConnectorResponse;
import com.eparly.api.catalogsync.api.CatalogSyncCandidateResponse;
import com.eparly.api.catalogsync.connector.CatalogConnectorRegistry;
import com.eparly.api.catalogsync.api.CatalogSyncPermissionRequest;
import com.eparly.api.catalogsync.api.CatalogSyncSourceCreateRequest;
import com.eparly.api.catalogsync.api.CatalogSyncSourceResponse;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import com.eparly.api.catalogsync.validation.SourceTrustPolicy;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.service.OfferAvailabilityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CatalogSyncAdminService {
    private final JdbcTemplate jdbc;
    private final CatalogSyncRepository repository;
    private final CatalogSyncJobService jobService;
    private final SourceTrustPolicy trustPolicy;
    private final ObjectMapper objectMapper;
    private final CommercialOfferRepository offerRepository;
    private final OfferAvailabilityService availabilityService;

    private final CatalogConnectorRegistry connectors;

    public CatalogSyncAdminService(JdbcTemplate jdbc, CatalogSyncRepository repository,
                                   CatalogSyncJobService jobService, SourceTrustPolicy trustPolicy,
                                   ObjectMapper objectMapper, CommercialOfferRepository offerRepository,
                                   OfferAvailabilityService availabilityService,
                                   CatalogConnectorRegistry connectors) {
        this.connectors = connectors;
        this.jdbc = jdbc;
        this.repository = repository;
        this.jobService = jobService;
        this.trustPolicy = trustPolicy;
        this.objectMapper = objectMapper;
        this.offerRepository = offerRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional
    public CatalogSyncSourceResponse createSource(CatalogSyncSourceCreateRequest request, UUID actorId) {
        String domain = normalizeDomain(request.officialDomain());
        String url = URI.create(request.sourceUrl().trim()).normalize().toString();
        String country = request.country() == null ? "FR" : request.country();
        validateProvider(request.providerId(), request.sector(), domain);
        CatalogSyncSource validationSource = new CatalogSyncSource(null, request.providerId(),
                request.sourceName().trim(), domain, url, request.sourceType(), request.sector(),
                false, "PENDING", null, null, request.extractorConfig());
        trustPolicy.validate(validationSource, url);
        String config = normalizeConfig(request.extractorConfig());
        String connectorKey = request.connectorKey() == null || request.connectorKey().isBlank()
                ? null : request.connectorKey().trim();
        if (connectorKey != null && !connectors.isKnown(connectorKey)) {
            throw new IllegalArgumentException("Unknown catalog connector");
        }

        UUID id = jdbc.queryForObject("""
                INSERT INTO catalog_sync_sources
                    (provider_id, source_name, official_domain, source_url, source_type,
                     sector, country, extractor_config, connector_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                RETURNING id
                """, UUID.class, request.providerId(), request.sourceName().trim(), domain, url,
                request.sourceType().name(), request.sector().name(), country, config, connectorKey);
        audit(actorId, "SOURCE_CREATED", "CATALOG_SYNC_SOURCE", id,
                "{\"permissionStatus\":\"PENDING\",\"collectionEnabled\":false}");
        return findSource(id);
    }

    @Transactional
    public CatalogSyncSourceResponse setPermission(UUID sourceId, CatalogSyncPermissionRequest request,
                                                   UUID actorId) {
        if (request.collectionEnabled()
                && request.permissionStatus() != CatalogSyncPermissionRequest.PermissionStatus.AUTHORIZED) {
            throw new IllegalArgumentException("Only an explicitly authorized source may be enabled");
        }
        int updated = jdbc.update("""
                UPDATE catalog_sync_sources
                SET permission_status = ?, collection_enabled = ?, permission_reason = ?,
                    permission_updated_by_user_id = ?, permission_updated_at = ?,
                    robots_status = 'UNKNOWN', last_error = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.permissionStatus().name(), request.collectionEnabled(), request.reason().trim(),
                actorId, java.sql.Timestamp.from(Instant.now()), sourceId);
        if (updated == 0) throw new IllegalArgumentException("Catalog sync source not found");
        audit(actorId, "SOURCE_PERMISSION_CHANGED", "CATALOG_SYNC_SOURCE", sourceId,
                "{\"permissionStatus\":\"" + request.permissionStatus().name()
                        + "\",\"collectionEnabled\":" + request.collectionEnabled() + "}");
        return findSource(sourceId);
    }

    public List<CatalogSyncSourceResponse> sources() {
        return jdbc.query("""
                SELECT id, provider_id, source_name, official_domain, source_url, source_type, sector,
                       country, collection_enabled, permission_status, permission_reason, robots_status,
                       last_checked_at, last_success_at, last_error, connector_key
                FROM catalog_sync_sources ORDER BY source_name, id
                """, (rs, row) -> new CatalogSyncSourceResponse(
                rs.getObject("id", UUID.class), rs.getObject("provider_id", UUID.class),
                rs.getString("source_name"), rs.getString("official_domain"), rs.getString("source_url"),
                rs.getString("source_type"), rs.getString("sector"), rs.getString("country"),
                rs.getBoolean("collection_enabled"), rs.getString("permission_status"),
                rs.getString("permission_reason"), rs.getString("robots_status"),
                instant(rs.getTimestamp("last_checked_at")), instant(rs.getTimestamp("last_success_at")),
                rs.getString("last_error"), rs.getString("connector_key")));
    }

    public List<CatalogConnectorResponse> connectors() {
        return connectors.all().stream().map(connector -> {
            var readiness = connector.readiness();
            return new CatalogConnectorResponse(connector.key(), readiness.ready(), readiness.reason());
        }).toList();
    }

    public List<com.eparly.api.catalogsync.domain.CatalogSyncSourceReport> runSources(UUID runId) {
        return jobService.runSources(runId);
    }

    public List<CatalogSyncCandidateResponse> candidates(String status, int limit) {
        String candidateStatus = status == null || status.isBlank() ? "NEEDS_REVIEW" : status.toUpperCase(Locale.ROOT);
        if (!List.of("NEEDS_REVIEW", "ACCEPTED", "REJECTED", "SUPERSEDED", "DRAFT_CREATED").contains(candidateStatus)) {
            throw new IllegalArgumentException("Unsupported catalog candidate status");
        }
        int boundedLimit = Math.max(1, Math.min(100, limit));
        return jdbc.query("""
                SELECT candidate.id, candidate.provider_id, candidate.existing_offer_id,
                       candidate.latest_observation_id, candidate.candidate_status, candidate.change_type,
                       candidate.detected_at, observation.validation_status, observation.confidence,
                       observation.normalized_data::text, observation.validation_reasons::text
                FROM catalog_offer_candidates candidate
                JOIN catalog_offer_observations observation ON observation.id = candidate.latest_observation_id
                WHERE candidate.candidate_status = ?
                ORDER BY candidate.detected_at DESC LIMIT ?
                """, (rs, row) -> new CatalogSyncCandidateResponse(
                rs.getObject("id", UUID.class), rs.getObject("provider_id", UUID.class),
                rs.getObject("existing_offer_id", UUID.class), rs.getObject("latest_observation_id", UUID.class),
                rs.getString("candidate_status"), rs.getString("change_type"),
                instant(rs.getTimestamp("detected_at")), rs.getString("validation_status"),
                rs.getDouble("confidence"), parseJson(rs.getString("normalized_data")),
                parseJson(rs.getString("validation_reasons"))), candidateStatus, boundedLimit);
    }

    @Transactional
    public void reviewCandidate(UUID candidateId, String status, UUID publishedOfferId, UUID actorId) {
        if ("REJECTED".equals(status)) {
            if (publishedOfferId != null) throw new IllegalArgumentException("Rejected candidates cannot link an offer");
            int updated = jdbc.update("""
                    UPDATE catalog_offer_candidates
                    SET candidate_status = 'REJECTED', reviewed_at = ?, reviewed_by_user_id = ?
                    WHERE id = ? AND candidate_status = 'NEEDS_REVIEW'
                    """, java.sql.Timestamp.from(Instant.now()), actorId, candidateId);
            if (updated == 0) throw new IllegalArgumentException("Candidate not found or already reviewed");
            audit(actorId, "CANDIDATE_REJECTED", "CATALOG_SYNC_CANDIDATE", candidateId, "{}");
            return;
        }
        if (!"ACCEPTED".equals(status) || publishedOfferId == null) {
            throw new IllegalArgumentException("Accepted candidates must link to a published verified offer");
        }
        CommercialOffer publishedOffer = offerRepository.findById(publishedOfferId)
                .orElseThrow(() -> new IllegalArgumentException("Published offer not found"));
        if (!availabilityService.evaluate(publishedOffer, publishedOffer.getTerritory()).available()) {
            throw new IllegalArgumentException("Candidate can only be accepted for a currently available offer");
        }
        int updated = jdbc.update("""
                UPDATE catalog_offer_candidates candidate
                SET candidate_status = 'ACCEPTED', existing_offer_id = ?, reviewed_at = ?,
                    reviewed_by_user_id = ?
                FROM catalog_offer_observations observation, commercial_offers offer, offer_pricing pricing
                WHERE candidate.id = ? AND candidate.latest_observation_id = observation.id
                  AND offer.id = ? AND pricing.offer_id = offer.id
                  AND offer.publication_status = 'VERIFIED'
                  AND (candidate.provider_id IS NULL OR candidate.provider_id = offer.provider_id)
                  AND offer.provider_id = COALESCE(candidate.provider_id, offer.provider_id)
                  AND pricing.amount_minor IS NOT DISTINCT FROM
                      NULLIF(observation.normalized_data->>'amountMinor', '')::BIGINT
                  AND pricing.currency IS NOT DISTINCT FROM
                      NULLIF(observation.normalized_data->>'currency', '')
                  AND pricing.billing_period IS NOT DISTINCT FROM
                      NULLIF(observation.normalized_data->>'billingPeriod', '')
                  AND candidate.candidate_status = 'NEEDS_REVIEW'
                """, publishedOfferId, java.sql.Timestamp.from(Instant.now()), actorId, candidateId,
                publishedOfferId);
        if (updated == 0) {
            throw new IllegalArgumentException("Candidate can only be accepted after matching a published offer and price");
        }
        audit(actorId, "CANDIDATE_ACCEPTED", "CATALOG_SYNC_CANDIDATE", candidateId,
                "{\"publishedOfferId\":\"" + publishedOfferId + "\"}");
    }

    public com.eparly.api.catalogsync.domain.CatalogSyncReport runNow() {
        return jobService.runNow();
    }

    public List<com.eparly.api.catalogsync.domain.CatalogSyncReport> recentRuns(int limit) {
        return jobService.recentRuns(limit);
    }

    private CatalogSyncSourceResponse findSource(UUID id) {
        return jdbc.query("""
                SELECT id, provider_id, source_name, official_domain, source_url, source_type, sector,
                       country, collection_enabled, permission_status, permission_reason, robots_status,
                       last_checked_at, last_success_at, last_error, connector_key
                FROM catalog_sync_sources WHERE id = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Catalog sync source not found");
            return new CatalogSyncSourceResponse(rs.getObject("id", UUID.class),
                    rs.getObject("provider_id", UUID.class), rs.getString("source_name"),
                    rs.getString("official_domain"), rs.getString("source_url"),
                    rs.getString("source_type"), rs.getString("sector"), rs.getString("country"),
                    rs.getBoolean("collection_enabled"), rs.getString("permission_status"),
                    rs.getString("permission_reason"), rs.getString("robots_status"),
                    instant(rs.getTimestamp("last_checked_at")), instant(rs.getTimestamp("last_success_at")),
                    rs.getString("last_error"), rs.getString("connector_key"));
        }, id);
    }

    private void validateProvider(UUID providerId, OfferSector sector, String domain) {
        if (providerId == null) return;
        jdbc.query("""
                SELECT sector, official_website FROM offer_providers WHERE id = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Provider does not exist");
            if (!sector.name().equals(rs.getString("sector"))) {
                throw new IllegalArgumentException("Source sector must match its provider");
            }
            String websiteHost = URI.create(rs.getString("official_website")).getHost();
            if (websiteHost == null || (!domain.equalsIgnoreCase(websiteHost)
                    && !domain.toLowerCase(Locale.ROOT).endsWith("." + websiteHost.toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException("Source domain must belong to the provider official website");
            }
            return null;
        }, providerId);
    }

    private String normalizeConfig(String config) {
        if (config == null || config.isBlank()) return "{}";
        try {
            JsonNode parsed = objectMapper.readTree(config);
            if (!parsed.isObject()) throw new IllegalArgumentException("Extractor config must be a JSON object");
            return objectMapper.writeValueAsString(parsed);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Extractor config must be valid JSON", exception);
        }
    }

    private static String normalizeDomain(String domain) {
        String value = domain.trim().toLowerCase(Locale.ROOT);
        URI uri = URI.create(value.contains("://") ? value : "https://" + value);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != -1
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("officialDomain must be a hostname without path or port");
        }
        return java.net.IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
    }

    private void audit(UUID actorId, String action, String resourceType, UUID resourceId, String details) {
        jdbc.update("""
                INSERT INTO catalog_admin_audit(actor_user_id, action, resource_type, resource_id, details)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """, actorId, action, resourceType, resourceId, details);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted catalog synchronization JSON is invalid", exception);
        }
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
