package com.eparly.api.catalogsync.persistence;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogPriceKind;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.normalization.CatalogDeduplicationService;
import com.eparly.api.catalogsync.normalization.NormalizedCatalogOffer;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Enregistre une observation et, dans la meme transaction, integre l'offre exploitable au catalogue :
 * fournisseur, offre DRAFT, tarif, preuve PENDING, historique. Rien n'est verifie ni publie ici. Le batch
 * ne modifie que les brouillons qu'il a crees lui-meme ; toute autre offre reste protegee et le changement
 * reste un candidat ouvert avec son motif.
 */
@Service
public class CatalogBusinessIngestionService {
    private final CatalogSyncRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CatalogSyncProperties properties;

    public CatalogBusinessIngestionService(CatalogSyncRepository repository, JdbcTemplate jdbc,
                                           ObjectMapper objectMapper, CatalogSyncProperties properties) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record Result(CatalogSyncRepository.ObservationResult observation, int providersCreated,
                         int offersCreated, int pricesChanged, int evidenceAdded) {
    }

    private record Link(UUID offerId, UUID ownerSourceId, boolean createdByBatch) {
    }

    @Transactional
    public Result ingest(CatalogSyncSource source, NormalizedCatalogOffer offer,
                         CatalogDeduplicationService.OfferIdentity identity, UUID runId, Instant observedAt) {
        CatalogSyncRepository.ObservationResult observation =
                repository.saveObservation(source, offer, identity, runId, observedAt);
        if (!properties.isBusinessIngestionEnabled()
                || offer.validationStatus() == CatalogObservationStatus.REJECTED
                || offer.validationStatus() == CatalogObservationStatus.UNSUPPORTED) {
            return new Result(observation, 0, 0, 0, 0);
        }
        String blocked = blockReason(offer, identity, source);
        if (blocked != null) {
            markCandidate(source, offer, null, blocked);
            return new Result(observation, 0, 0, 0, 0);
        }
        Link link = findLink(identity.identityKey());
        return link == null
                ? createOrLink(source, offer, identity, observation, observedAt)
                : update(source, offer, link, observation, observedAt);
    }

    /** Motif precis pour lequel l'offre reste candidate ; rien n'est deduit pour combler un manque. */
    private static String blockReason(NormalizedCatalogOffer offer, CatalogDeduplicationService.OfferIdentity identity,
                                      CatalogSyncSource source) {
        if (identity == null || offer.sector() == null) return "IDENTITY_UNKNOWN";
        if (offer.providerName() == null && source.providerId() == null) return "PROVIDER_UNKNOWN";
        if (offer.amountMinor() == null) return "PRICE_UNKNOWN";
        if (offer.currency() == null) return "CURRENCY_UNKNOWN";
        if (offer.billingPeriod() == null || offer.billingPeriod() == OfferBillingPeriod.UNKNOWN) {
            return "BILLING_PERIOD_UNKNOWN";
        }
        if (offer.canonicalUrl() == null) return "SOURCE_URL_UNKNOWN";
        if (offer.name().length() > 200) return "NAME_TOO_LONG";
        if (offer.validUntil() != null && offer.validUntil().isBefore(LocalDate.now())) return "OFFER_EXPIRED";
        return null;
    }

    private Result createOrLink(CatalogSyncSource source, NormalizedCatalogOffer offer,
                                CatalogDeduplicationService.OfferIdentity identity,
                                CatalogSyncRepository.ObservationResult observation, Instant observedAt) {
        boolean[] providerCreated = {false};
        UUID providerId = source.providerId() != null ? source.providerId()
                : findOrCreateProvider(offer, providerCreated);
        UUID existing = jdbc.query("""
                SELECT id FROM commercial_offers WHERE provider_id = ? AND lower(commercial_name) = lower(?)
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, providerId, offer.name());
        if (existing != null) {
            // Offre existante non creee par le batch : liee pour le suivi, jamais modifiee.
            setLink(identity.identityKey(), existing, source.id(), false);
            markCandidate(source, offer, existing, "EXISTING_OFFER_NOT_MANAGED_BY_BATCH");
            return new Result(observation, providerCreated[0] ? 1 : 0, 0, 0, 0);
        }
        UUID offerId = jdbc.queryForObject("""
                INSERT INTO commercial_offers
                    (provider_id, commercial_name, sector, characteristics, eligibility_conditions, commitment,
                     territory, publication_status, valid_from, valid_until, reference_kind)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                RETURNING id
                """, UUID.class, providerId, offer.name(), offer.sector().name(), json(offer.characteristics()),
                conditions(offer), commitment(offer), offer.country() == null ? "FR" : offer.country(),
                date(offer.validFrom()), date(offer.validUntil()), referenceKind(offer));
        Map<String, Object> pricing = pricing(offer);
        jdbc.update("""
                INSERT INTO offer_pricing
                    (offer_id, amount_minor, currency, billing_period, activation_fee_minor, promotion_price_type,
                     price_after_promotion_minor, promotion_duration_months, promotion_valid_until,
                     discount_conditions, taxes_included, data_unknown)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, offerId, pricing.get("amountMinor"), pricing.get("currency"), pricing.get("billingPeriod"),
                pricing.get("activationFeeMinor"), pricing.get("promotionPriceType"),
                pricing.get("priceAfterPromotionMinor"), pricing.get("promotionDurationMonths"),
                date(offer.promotionValidUntil() != null && pricing.get("promotionPriceType") != null
                        ? offer.promotionValidUntil() : null),
                pricing.get("discountConditions"), pricing.get("taxesIncluded"), pricing.get("dataUnknown"));
        insertHistory(offerId, null, pricing, "Premiere observation automatique", observedAt);
        int evidence = recordEvidence(offerId, source, offer, observedAt);
        setLink(identity.identityKey(), offerId, source.id(), true);
        markCandidate(source, offer, offerId, null);
        return new Result(observation, providerCreated[0] ? 1 : 0, 1, 0, evidence);
    }

    private Result update(CatalogSyncSource source, NormalizedCatalogOffer offer, Link link,
                          CatalogSyncRepository.ObservationResult observation, Instant observedAt) {
        String status = jdbc.queryForObject("SELECT publication_status FROM commercial_offers WHERE id = ?",
                String.class, link.offerId());
        if (!link.createdByBatch() || !"DRAFT".equals(status)) {
            markCandidate(source, offer, link.offerId(), link.createdByBatch()
                    ? "VALIDATED_OFFER_REQUIRES_REVIEW" : "EXISTING_OFFER_NOT_MANAGED_BY_BATCH");
            return new Result(observation, 0, 0, 0, 0);
        }
        int evidence = recordEvidence(link.offerId(), source, offer, observedAt);
        if (!source.id().equals(link.ownerSourceId())) {
            // Autre source : preuve supplementaire. Son tarif ne remplace pas celui de la source de reference.
            markCandidate(source, offer, link.offerId(), "TARIFF_OWNED_BY_ANOTHER_SOURCE");
            return new Result(observation, 0, 0, 0, evidence);
        }
        Map<String, Object> current = currentPricing(link.offerId());
        Map<String, Object> next = pricing(offer);
        int changed = 0;
        if (!sameSnapshot(current, next)) {
            jdbc.update("""
                    UPDATE offer_pricing
                    SET amount_minor = ?, currency = ?, billing_period = ?, activation_fee_minor = ?,
                        promotion_price_type = ?, price_after_promotion_minor = ?, promotion_duration_months = ?,
                        promotion_valid_until = ?, discount_conditions = ?, taxes_included = ?, data_unknown = ?,
                        version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE offer_id = ?
                    """, next.get("amountMinor"), next.get("currency"), next.get("billingPeriod"),
                    next.get("activationFeeMinor"), next.get("promotionPriceType"),
                    next.get("priceAfterPromotionMinor"), next.get("promotionDurationMonths"),
                    date(next.get("promotionPriceType") != null ? offer.promotionValidUntil() : null),
                    next.get("discountConditions"), next.get("taxesIncluded"), next.get("dataUnknown"),
                    link.offerId());
            insertHistory(link.offerId(), current, next, "Changement observe automatiquement", observedAt);
            changed = 1;
        }
        jdbc.update("""
                UPDATE commercial_offers
                SET characteristics = ?, eligibility_conditions = ?, commitment = ?, valid_from = ?, valid_until = ?,
                    reference_kind = ?,
                    updated_at = CASE WHEN characteristics IS DISTINCT FROM ? OR eligibility_conditions IS DISTINCT FROM ?
                                      THEN CURRENT_TIMESTAMP ELSE updated_at END
                WHERE id = ?
                """, json(offer.characteristics()), conditions(offer), commitment(offer), date(offer.validFrom()),
                date(offer.validUntil()), referenceKind(offer), json(offer.characteristics()), conditions(offer),
                link.offerId());
        markCandidate(source, offer, link.offerId(), null);
        return new Result(observation, 0, 0, changed, evidence);
    }

    private UUID findOrCreateProvider(NormalizedCatalogOffer offer, boolean[] created) {
        String name = offer.providerName();
        UUID existing = findProvider(name, offer);
        if (existing != null) return existing;
        URI uri = URI.create(offer.canonicalUrl());
        int inserted = jdbc.update("""
                INSERT INTO offer_providers (name, sector, official_website, country)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (lower(name), sector) DO NOTHING
                """, name, offer.sector().name(), "https://" + uri.getHost(),
                offer.country() == null ? "FR" : offer.country());
        created[0] = inserted == 1;
        return findProvider(name, offer);
    }

    private UUID findProvider(String name, NormalizedCatalogOffer offer) {
        return jdbc.query("SELECT id FROM offer_providers WHERE lower(name) = lower(?) AND sector = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, name, offer.sector().name());
    }

    private Link findLink(String identityKey) {
        return jdbc.query("""
                SELECT commercial_offer_id, owner_source_id, created_by_batch
                FROM catalog_offer_identities WHERE identity_key = ? AND commercial_offer_id IS NOT NULL
                """, rs -> rs.next() ? new Link(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getBoolean(3)) : null, identityKey);
    }

    private void setLink(String identityKey, UUID offerId, UUID sourceId, boolean createdByBatch) {
        jdbc.update("""
                UPDATE catalog_offer_identities
                SET commercial_offer_id = ?, owner_source_id = ?, created_by_batch = ?
                WHERE identity_key = ?
                """, offerId, sourceId, createdByBatch, identityKey);
    }

    /** Une preuve PENDING par offre et par URL ; elle n'est ni verifiee ni dupliquee. Retourne le nombre ajoute. */
    private int recordEvidence(UUID offerId, CatalogSyncSource source, NormalizedCatalogOffer offer, Instant observedAt) {
        int refreshed = jdbc.update("""
                UPDATE offer_evidence SET consulted_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE offer_id = ? AND official_url = ? AND verification_status = 'PENDING'
                """, Timestamp.from(observedAt), offerId, offer.canonicalUrl());
        if (refreshed > 0) return 0;
        Integer any = jdbc.queryForObject("SELECT COUNT(*) FROM offer_evidence WHERE offer_id = ? AND official_url = ?",
                Integer.class, offerId, offer.canonicalUrl());
        if (any != null && any > 0) return 0;
        String provenance = "catalog-sync: " + source.sourceName();
        return jdbc.update("""
                INSERT INTO offer_evidence
                    (offer_id, official_url, consulted_at, verified_at, nature, verification_status, provenance,
                     evidence_scope)
                VALUES (?, ?, ?, NULL, 'Observation automatique a verifier par une personne', 'PENDING', ?, 'OFFER')
                """, offerId, offer.canonicalUrl(), Timestamp.from(observedAt),
                provenance.length() > 200 ? provenance.substring(0, 200) : provenance);
    }

    private void markCandidate(CatalogSyncSource source, NormalizedCatalogOffer offer, UUID offerId, String reason) {
        jdbc.update("""
                UPDATE catalog_offer_candidates candidate
                SET existing_offer_id = COALESCE(?, candidate.existing_offer_id), business_block_reason = ?,
                    candidate_status = CASE WHEN ? THEN 'DRAFT_CREATED' ELSE candidate.candidate_status END
                FROM catalog_source_offer_states state
                WHERE state.source_id = ? AND state.offer_external_key = ?
                  AND candidate.latest_observation_id = state.latest_observation_id
                  AND candidate.candidate_status = 'NEEDS_REVIEW'
                """, offerId, reason, reason == null, source.id(), offer.externalKey());
    }

    /**
     * Tarif de travail. data_unknown reste vrai des qu'une donnee manque ou que le prix depend de l'usage ou du
     * profil : la publication est alors refusee tant qu'une personne n'a pas revu le tarif. Une promotion
     * expiree n'est plus portee par le tarif ; elle reste dans l'historique et les observations.
     */
    private static Map<String, Object> pricing(NormalizedCatalogOffer offer) {
        boolean structuredPromotion = !offer.promotionExpired() && offer.promotionAmountMinor() != null
                && offer.promotionDurationMonths() != null && offer.promotionDurationMonths() > 0
                && offer.promotionAmountMinor() <= offer.amountMinor();
        CatalogPriceKind kind = offer.priceKind();
        boolean reliable = offer.taxesIncluded() != null && !offer.startingAtPrice()
                && (kind == CatalogPriceKind.FIXED || kind == CatalogPriceKind.FREE
                || kind == CatalogPriceKind.PROMOTIONAL);
        String discount = offer.promotionExpired() ? null : offer.promotionText();
        Map<String, Object> pricing = new LinkedHashMap<>();
        pricing.put("amountMinor", offer.amountMinor());
        pricing.put("currency", offer.currency());
        pricing.put("billingPeriod", offer.billingPeriod().name());
        pricing.put("activationFeeMinor", offer.activationFeeMinor());
        pricing.put("promotionPriceType", structuredPromotion ? "FIXED_PRICE" : null);
        pricing.put("priceAfterPromotionMinor", structuredPromotion ? offer.promotionAmountMinor() : null);
        pricing.put("promotionDurationMonths", structuredPromotion ? offer.promotionDurationMonths() : null);
        pricing.put("promotionValidUntil", structuredPromotion && offer.promotionValidUntil() != null
                ? offer.promotionValidUntil().toString() : null);
        pricing.put("discountConditions", discount == null ? null
                : discount.length() > 500 ? discount.substring(0, 500) : discount);
        pricing.put("taxesIncluded", offer.taxesIncluded() == null || offer.taxesIncluded());
        pricing.put("dataUnknown", !reliable);
        return pricing;
    }

    private Map<String, Object> currentPricing(UUID offerId) {
        return jdbc.queryForObject("""
                SELECT amount_minor, currency, billing_period, activation_fee_minor, promotion_price_type,
                       price_after_promotion_minor, promotion_duration_months, promotion_valid_until,
                       discount_conditions, taxes_included, data_unknown
                FROM offer_pricing WHERE offer_id = ?
                """, (rs, row) -> {
            Map<String, Object> pricing = new LinkedHashMap<>();
            pricing.put("amountMinor", rs.getObject("amount_minor"));
            pricing.put("currency", rs.getString("currency"));
            pricing.put("billingPeriod", rs.getString("billing_period"));
            pricing.put("activationFeeMinor", rs.getObject("activation_fee_minor"));
            pricing.put("promotionPriceType", rs.getString("promotion_price_type"));
            pricing.put("priceAfterPromotionMinor", rs.getObject("price_after_promotion_minor"));
            pricing.put("promotionDurationMonths", rs.getObject("promotion_duration_months"));
            Date until = rs.getDate("promotion_valid_until");
            pricing.put("promotionValidUntil", until == null ? null : until.toLocalDate().toString());
            pricing.put("discountConditions", rs.getString("discount_conditions"));
            pricing.put("taxesIncluded", rs.getBoolean("taxes_included"));
            pricing.put("dataUnknown", rs.getBoolean("data_unknown"));
            return pricing;
        }, offerId);
    }

    private static boolean sameSnapshot(Map<String, Object> left, Map<String, Object> right) {
        for (Map.Entry<String, Object> entry : right.entrySet()) {
            Object a = left.get(entry.getKey());
            Object b = entry.getValue();
            boolean same = a instanceof Number x && b instanceof Number y
                    ? x.longValue() == y.longValue() : Objects.equals(a, b);
            if (!same) return false;
        }
        return true;
    }

    private void insertHistory(UUID offerId, Map<String, Object> previous, Map<String, Object> next, String reason,
                               Instant observedAt) {
        jdbc.update("""
                INSERT INTO offer_price_history
                    (offer_id, old_amount_minor, new_amount_minor, changed_at, reason, old_pricing_snapshot,
                     new_pricing_snapshot)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, offerId, previous == null || previous.get("amountMinor") == null
                        ? null : ((Number) previous.get("amountMinor")).longValue(),
                next.get("amountMinor"), Timestamp.from(observedAt), reason,
                previous == null ? null : json(previous), json(next));
    }

    private static String referenceKind(NormalizedCatalogOffer offer) {
        Object nature = offer.characteristics() == null ? null : offer.characteristics().get("offerNature");
        return nature != null && nature.toString().startsWith("REFERENCE") ? nature.toString() : null;
    }

    private static String conditions(NormalizedCatalogOffer offer) {
        String joined = String.join("\n", offer.conditions() == null ? java.util.List.of() : offer.conditions());
        if (offer.eligibility() != null) joined = joined.isEmpty() ? offer.eligibility() : offer.eligibility() + "\n" + joined;
        return joined.isEmpty() ? null : joined;
    }

    private static String commitment(NormalizedCatalogOffer offer) {
        return offer.commitmentMonths() == null ? null : offer.commitmentMonths() + " mois";
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
