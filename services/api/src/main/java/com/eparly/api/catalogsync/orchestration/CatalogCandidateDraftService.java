package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.catalogsync.api.CatalogCandidateDraftRequest;
import com.eparly.api.offer.admin.dto.EvidenceCreateRequest;
import com.eparly.api.offer.admin.dto.OfferDraftRequest;
import com.eparly.api.offer.admin.dto.PricingUpsertRequest;
import com.eparly.api.offer.admin.service.CatalogAdminService;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.dto.CommercialOfferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Transforme, sur decision d'une personne, un candidat en offre DRAFT avec preuve PENDING.
 * Toutes les ecritures du catalogue passent par CatalogAdminService et ses regles ; ce service
 * n'est jamais appele par le batch et ne verifie ni ne publie rien.
 */
@Service
public class CatalogCandidateDraftService {
    private final JdbcTemplate jdbc;
    private final CatalogAdminService catalogAdmin;
    private final ObjectMapper objectMapper;

    public CatalogCandidateDraftService(JdbcTemplate jdbc, CatalogAdminService catalogAdmin,
                                        ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.catalogAdmin = catalogAdmin;
        this.objectMapper = objectMapper;
    }

    private record Candidate(String status, String validationStatus, String normalizedData, String canonicalUrl,
                             Instant observedAt, String sourceName) {
    }

    @Transactional
    public CommercialOfferResponse createDraft(UUID candidateId, CatalogCandidateDraftRequest request, UUID actorId) {
        Candidate candidate = jdbc.query("""
                SELECT candidate.candidate_status, observation.validation_status,
                       observation.normalized_data::text AS normalized_data, observation.canonical_url,
                       observation.last_observed_at, source.source_name
                FROM catalog_offer_candidates candidate
                JOIN catalog_offer_observations observation ON observation.id = candidate.latest_observation_id
                JOIN catalog_sync_sources source ON source.id = candidate.source_id
                WHERE candidate.id = ?
                FOR UPDATE OF candidate
                """, rs -> rs.next() ? new Candidate(rs.getString("candidate_status"),
                rs.getString("validation_status"), rs.getString("normalized_data"), rs.getString("canonical_url"),
                rs.getTimestamp("last_observed_at").toInstant(), rs.getString("source_name")) : null, candidateId);
        if (candidate == null) throw new ApiException(HttpStatus.NOT_FOUND, "Candidate not found");
        if (!"NEEDS_REVIEW".equals(candidate.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a candidate awaiting review can become a draft");
        }

        JsonNode data = objectMapper.readTree(candidate.normalizedData());
        String sectorName = text(data, "sector");
        if (sectorName == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Candidate has no sector");
        String name = request.commercialName() != null && !request.commercialName().isBlank()
                ? request.commercialName() : text(data, "name");
        String territory = request.territory() == null || request.territory().isBlank()
                ? text(data, "country") : request.territory();

        CommercialOfferResponse draft = catalogAdmin.createDraft(actorId, new OfferDraftRequest(
                request.providerId(), name, OfferSector.valueOf(sectorName),
                data.path("characteristics").isObject() ? data.path("characteristics").toString() : null,
                text(data, "eligibility"), commitment(data), territory == null ? "FR" : territory,
                date(data, "validFrom"), date(data, "validUntil")));

        String currency = text(data, "currency");
        if (currency != null) {
            catalogAdmin.upsertPricing(actorId, draft.id(), pricing(data, currency, candidateId));
        }
        String evidenceUrl = request.evidenceUrl() != null && !request.evidenceUrl().isBlank()
                ? request.evidenceUrl() : candidate.canonicalUrl();
        catalogAdmin.addEvidence(actorId, draft.id(), new EvidenceCreateRequest(evidenceUrl, OfferEvidenceScope.OFFER,
                "Observation automatique a verifier par une personne",
                truncate("catalog-sync: " + candidate.sourceName(), 200), candidate.observedAt(), null));

        jdbc.update("""
                UPDATE catalog_offer_candidates
                SET candidate_status = 'DRAFT_CREATED', existing_offer_id = ?, reviewed_at = ?, reviewed_by_user_id = ?
                WHERE id = ?
                """, draft.id(), Timestamp.from(Instant.now()), actorId, candidateId);
        jdbc.update("""
                INSERT INTO catalog_admin_audit(actor_user_id, action, resource_type, resource_id, details)
                VALUES (?, 'CANDIDATE_DRAFT_CREATED', 'CATALOG_SYNC_CANDIDATE', ?, CAST(? AS jsonb))
                """, actorId, candidateId, "{\"offerId\":\"" + draft.id() + "\"}");
        return catalogAdmin.getOffer(draft.id());
    }

    /**
     * Le tarif est marque peu fiable des qu'une donnee manque ou depend de l'usage ou du profil : la
     * publication reste alors refusee tant qu'une personne ne l'a pas revu. Les promotions observees
     * restent du texte a verifier : leur structure n'est pas deduite.
     */
    private static PricingUpsertRequest pricing(JsonNode data, String currency, UUID candidateId) {
        Long amount = longValue(data, "amountMinor");
        String period = text(data, "billingPeriod");
        Boolean taxes = data.path("taxesIncluded").isBoolean() ? data.path("taxesIncluded").booleanValue() : null;
        String kind = text(data, "priceKind");
        boolean unreliable = amount == null || period == null || taxes == null
                || !("FIXED".equals(kind) || "FREE".equals(kind));
        String promotion = text(data, "promotionText");
        return new PricingUpsertRequest(null, amount, currency,
                period == null ? OfferBillingPeriod.UNKNOWN : OfferBillingPeriod.valueOf(period),
                longValue(data, "activationFeeMinor"), null, null, null, null, null, null, null,
                promotion == null ? null : truncate(promotion, 500), taxes == null ? Boolean.TRUE : taxes, unreliable,
                truncate("Issu du candidat de collecte " + candidateId, 500));
    }

    private static String commitment(JsonNode data) {
        Long months = longValue(data, "commitmentMonths");
        return months == null ? null : months + " mois";
    }

    private static LocalDate date(JsonNode data, String field) {
        String value = text(data, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static Long longValue(JsonNode data, String field) {
        JsonNode value = data.get(field);
        return value == null || !value.isIntegralNumber() ? null : value.longValue();
    }

    private static String text(JsonNode data, String field) {
        JsonNode value = data.get(field);
        return value == null || value.isNull() || value.isArray() || value.isObject() ? null : value.asText();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
