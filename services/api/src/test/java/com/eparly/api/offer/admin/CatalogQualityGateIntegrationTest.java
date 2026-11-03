package com.eparly.api.offer.admin;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.catalogimport.CatalogImportReport;
import com.eparly.api.catalogimport.ManualCatalogImportService;
import com.eparly.api.offer.admin.dto.EvidenceVerificationRequest;
import com.eparly.api.offer.admin.dto.OfferReuseRightsRequest;
import com.eparly.api.offer.admin.service.CatalogAdminService;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferReuseRights;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ecritures reelles sur un PostgreSQL jetable, sans transaction de test : les rollbacks sont ceux du service. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@TestPropertySource(properties = "eparly.catalog-import.actor-user-id=22222222-3333-4444-5555-666666666666")
class CatalogQualityGateIntegrationTest {
    private static final UUID REQUESTER = UUID.fromString("22222222-3333-4444-5555-666666666666");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private ManualCatalogImportService importer;
    @Autowired private CatalogAdminService admin;
    @Autowired private JdbcTemplate jdbc;
    @TempDir Path dir;

    private String name() {
        return "Offre " + UUID.randomUUID();
    }

    private void ensureRequester() {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, status, email_verified_at, token_version, created_at, updated_at)
                VALUES (?, 'requester@example.test', 'hash', 'ACTIVE', now(), 1, now(), now()) ON CONFLICT (id) DO NOTHING
                """, REQUESTER);
    }

    private String provider() throws Exception {
        ensureRequester();
        String key = "Fournisseur " + UUID.randomUUID();
        Path file = dir.resolve("providers-" + UUID.randomUUID() + ".json");
        Files.writeString(file, """
                {"providers":[{"key":"p","name":"%s","sector":"MOBILE_PLAN","officialWebsite":"https://operator.example","country":"FR"}]}
                """.formatted(key));
        assertThat(importer.importFile(file, true).errors()).isEmpty();
        return key;
    }

    private CatalogImportReport importOffer(String provider, String offer, String pricing, String evidence) throws Exception {
        Path file = dir.resolve("offer-" + UUID.randomUUID() + ".json");
        Files.writeString(file, """
                {"offers":[{"providerKey":"%s","commercialName":"%s","sector":"MOBILE_PLAN","territory":"FR",
                "commitment":"Sans engagement","characteristics":{"network5G":true},
                "pricing":{"amountMinor":1000,"currency":"EUR","billingPeriod":"MONTHLY","reason":"Test"%s},
                "evidence":{"officialUrl":"https://operator.example/offre","nature":"Test","provenance":"Test"%s}}]}
                """.formatted(provider, offer, pricing, evidence));
        return importer.importFile(file, true);
    }

    private static String consultedAt(Instant at) {
        return ",\"consultedAt\":\"" + at + "\"";
    }

    private UUID offerId(String name) {
        return jdbc.queryForObject("SELECT id FROM commercial_offers WHERE commercial_name = ?", UUID.class, name);
    }

    private long offerVersion(UUID id) {
        return jdbc.queryForObject("SELECT version FROM commercial_offers WHERE id = ?", Long.class, id);
    }

    private void verify(UUID id) {
        var evidence = jdbc.queryForMap("SELECT id, version FROM offer_evidence WHERE offer_id = ?", id);
        admin.verifyEvidence(REQUESTER, id, (UUID) evidence.get("id"),
                new EvidenceVerificationRequest((Long) evidence.get("version"), EvidenceStatus.VERIFIED, "Verifie"));
    }

    private void assertPublishRefused(UUID id, String message) {
        assertThatThrownBy(() -> admin.publish(REQUESTER, id)).isInstanceOf(ApiException.class).hasMessageContaining(message);
        assertThat(jdbc.queryForObject("SELECT publication_status FROM commercial_offers WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    void importIsRecordedAsSystemImportNeverAsTheRequesterAction() throws Exception {
        String provider = provider();
        String offer = name();
        assertThat(importOffer(provider, offer, ",\"dataUnknown\":true", consultedAt(Instant.now().minusSeconds(60))).errors()).isEmpty();
        UUID id = offerId(offer);

        assertThat(jdbc.queryForList("""
                SELECT action FROM catalog_admin_audit
                WHERE actor_user_id IS NULL AND execution_mode = 'SYSTEM_IMPORT' AND requested_by_user_id = ?
                  AND (resource_id = ? OR details ->> 'offerId' = ?)
                """, String.class, REQUESTER, id, id.toString()))
                .containsExactlyInAnyOrder("OFFER_DRAFT_CREATED", "PRICING_CREATED", "EVIDENCE_ADDED");
        assertThat(jdbc.queryForObject("SELECT verification_status FROM offer_evidence WHERE offer_id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void failedEvidenceRollsBackTheWholeOffer() throws Exception {
        String provider = provider();
        String future = name();
        String missingDate = name();

        assertThat(importOffer(provider, future, "", consultedAt(Instant.now().plusSeconds(3600))).errors()).hasSize(1);
        assertThat(importOffer(provider, missingDate, "", "").errors()).hasSize(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commercial_offers WHERE commercial_name IN (?, ?)",
                Integer.class, future, missingDate)).isZero();
    }

    @Test
    void undeclaredDataUnknownStaysUnknownAndBlocksPublication() throws Exception {
        String offer = name();
        importOffer(provider(), offer, "", consultedAt(Instant.now().minusSeconds(60)));
        UUID id = offerId(offer);
        assertThat(jdbc.queryForObject("SELECT data_unknown FROM offer_pricing WHERE offer_id = ?", Boolean.class, id)).isTrue();
        assertPublishRefused(id, "reliable");
    }

    @Test
    void automaticImportCannotBePublishedWithoutEveryHumanAndDataRequirement() throws Exception {
        String offer = name();
        importOffer(provider(), offer, ",\"dataUnknown\":false", consultedAt(Instant.now().minusSeconds(60)));
        UUID id = offerId(offer);
        assertThat(jdbc.queryForObject("SELECT reuse_rights_status FROM commercial_offers WHERE id = ?", String.class, id))
                .isEqualTo("NOT_ESTABLISHED");

        assertPublishRefused(id, "verified");
        verify(id);
        assertThat(jdbc.queryForObject("""
                SELECT execution_mode FROM catalog_admin_audit WHERE action = 'EVIDENCE_VERIFIED' AND details ->> 'offerId' = ?
                """, String.class, id.toString())).isEqualTo("MANUAL_VERIFICATION");
        assertPublishRefused(id, "reuse rights");

        admin.updateReuseRights(REQUESTER, id, new OfferReuseRightsRequest(offerVersion(id), OfferReuseRights.ESTABLISHED, "Accord ecrit"));
        assertPublishRefused(id, "activation fees");

        jdbc.update("UPDATE offer_pricing SET activation_fee_minor = 0 WHERE offer_id = ?", id);
        jdbc.update("UPDATE commercial_offers SET commitment = NULL WHERE id = ?", id);
        assertPublishRefused(id, "commitment");

        jdbc.update("UPDATE commercial_offers SET commitment = 'Sans engagement' WHERE id = ?", id);
        jdbc.update("UPDATE offer_pricing SET promotion_duration_months = 12 WHERE offer_id = ?", id);
        assertPublishRefused(id, "complete promotion");

        jdbc.update("UPDATE offer_pricing SET promotion_duration_months = NULL WHERE offer_id = ?", id);
        jdbc.update("UPDATE offer_evidence SET official_url = 'https://operator.example/' WHERE offer_id = ?", id);
        assertPublishRefused(id, "own page");

        jdbc.update("UPDATE offer_evidence SET official_url = 'https://operator.example/offre' WHERE offer_id = ?", id);
        assertThat(admin.publish(REQUESTER, id).publicationStatus().name()).isEqualTo("VERIFIED");

        admin.updateReuseRights(REQUESTER, id, new OfferReuseRightsRequest(offerVersion(id), OfferReuseRights.RESTRICTED, "Accord retire"));
        assertThat(jdbc.queryForObject("SELECT publication_status FROM commercial_offers WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    void revisionUsesBusinessServicesKeepsHistoryAndIsNotReplayed() throws Exception {
        String provider = provider();
        String offer = name();
        importOffer(provider, offer, ",\"dataUnknown\":false", consultedAt(Instant.now().minusSeconds(60)));
        UUID id = offerId(offer);
        Path revisions = Files.createDirectories(dir.resolve("revisions")).resolve("r.json");
        Files.writeString(revisions, """
                {"revisions":[{"providerKey":"%s","commercialName":"%s","sector":"MOBILE_PLAN","expectedPricingVersion":0,
                "eligibilityConditions":"Conditions precises",
                "pricing":{"amountMinor":1000,"currency":"EUR","billingPeriod":"MONTHLY","activationFeeMinor":1000,
                "promotionPriceType":"FIXED_PRICE","promotionDurationMonths":12,"priceAfterPromotionMinor":700,
                "promotionValidFrom":"2026-07-09","taxesIncluded":true,"dataUnknown":false,"reason":"Correction"},
                "evidences":[{"officialUrl":"https://operator.example/contrat.pdf","consultedAt":"%s","evidenceScope":"PROMOTION",
                "nature":"Contrat","provenance":"Lecture automatisee"}]}]}
                """.formatted(provider, offer, Instant.now().minusSeconds(30)));

        assertThat(importer.importFile(revisions, false).actions()).anyMatch(a -> a.startsWith("REVISION_PLANNED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offer_price_history WHERE offer_id = ?", Integer.class, id)).isEqualTo(1);

        assertThat(importer.importFile(revisions, true).errors()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offer_price_history WHERE offer_id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT old_amount_minor FROM offer_price_history WHERE offer_id = ? AND old_amount_minor IS NOT NULL", Long.class, id)).isEqualTo(1000L);
        assertThat(jdbc.queryForObject("SELECT execution_mode FROM catalog_admin_audit WHERE action = 'PRICING_REVISED' AND resource_id = ?", String.class, id))
                .isEqualTo("SYSTEM_IMPORT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offer_evidence WHERE offer_id = ? AND evidence_scope = 'PROMOTION' AND verification_status = 'PENDING'", Integer.class, id)).isEqualTo(1);

        assertThat(importer.importFile(revisions, true).actions()).anyMatch(a -> a.startsWith("REVISION_SKIPPED_VERSION_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offer_price_history WHERE offer_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void auditRowWithoutActorIsOnlyAcceptedForSystemImports() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO catalog_admin_audit (action, resource_type, resource_id, execution_mode)
                VALUES ('X', 'OFFER', gen_random_uuid(), 'MANUAL_VERIFICATION')
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
