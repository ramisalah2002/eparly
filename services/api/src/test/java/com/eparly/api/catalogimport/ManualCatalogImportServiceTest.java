package com.eparly.api.catalogimport;

import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferProviderRepository;
import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@TestPropertySource(properties = "eparly.catalog-import.actor-user-id=11111111-2222-3333-4444-555555555555")
@Transactional
class ManualCatalogImportServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ManualCatalogImportService service;

    @Autowired
    private OfferProviderRepository providerRepository;

    @Autowired
    private CommercialOfferRepository offerRepository;

    @Autowired
    private OfferEvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void ensureActorUser() {
        UUID actorId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        if (userRepository.existsById(actorId)) return;
        java.time.Instant now = java.time.Instant.now();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, status, email_verified_at, token_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, actorId, "import-actor@example.com", "hash", "ACTIVE", java.sql.Timestamp.from(now), 1,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @Test
    @DisplayName("Dry-run does not create providers or offers")
    void dryRunDoesNotPersist() throws Exception {
        Path dir = Files.createTempDirectory("catalog-import-dry");
        Files.writeString(dir.resolve("providers.json"), """
                {"version":"1","providers":[{"key":"p1","name":"Dry Provider","sector":"MOBILE_PLAN","officialWebsite":"https://example.com","country":"FR"}]}
                """);
        Files.writeString(dir.resolve("mobile-offers.json"), """
                {"version":"1","offers":[{"providerKey":"p1","commercialName":"Dry Offer","sector":"MOBILE_PLAN","territory":"FR","characteristics":{"dataGb":10},"pricing":{"amountMinor":999,"currency":"EUR","billingPeriod":"MONTHLY","taxesIncluded":true,"dataUnknown":false},"evidence":{"officialUrl":"https://example.com/offer","consultedAt":"2026-01-01T00:00:00Z","nature":"Tarif","provenance":"Test"}}]}
                """);

        var reports = service.importDirectory(dir, false);
        assertThat(reports).isNotEmpty();
        assertThat(providerRepository.existsByNameIgnoreCaseAndSector("Dry Provider", OfferSector.MOBILE_PLAN)).isFalse();
        assertThat(offerRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Apply creates provider, draft offer, pricing and pending evidence")
    void applyCreatesDraft() throws Exception {
        Path dir = Files.createTempDirectory("catalog-import-apply");
        Files.writeString(dir.resolve("providers.json"), """
                {"version":"1","providers":[{"key":"p2","name":"Apply Provider","sector":"MOBILE_PLAN","officialWebsite":"https://example.com","country":"FR"}]}
                """);
        Files.writeString(dir.resolve("mobile-offers.json"), """
                {"version":"1","offers":[{"providerKey":"p2","commercialName":"Apply Offer","sector":"MOBILE_PLAN","territory":"FR","characteristics":{"dataGb":10},"pricing":{"amountMinor":999,"currency":"EUR","billingPeriod":"MONTHLY","taxesIncluded":true,"dataUnknown":false},"evidence":{"officialUrl":"https://example.com/offer","consultedAt":"2026-01-01T00:00:00Z","nature":"Tarif","provenance":"Test"}}]}
                """);

        ensureActorUser();
        var reports = service.importDirectory(dir, true);
        assertThat(reports).isNotEmpty();
        OfferProvider provider = providerRepository.findByNameIgnoreCaseAndSector("Apply Provider", OfferSector.MOBILE_PLAN).orElseThrow();
        assertThat(provider).isNotNull();
        assertThat(offerRepository.findAll()).hasSize(1);
        var offer = offerRepository.findAll().get(0);
        assertThat(offer.getPublicationStatus().name()).isEqualTo("DRAFT");
        assertThat(evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offer.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Second import is idempotent")
    void secondImportNoDuplicates() throws Exception {
        Path dir = Files.createTempDirectory("catalog-import-idempotent");
        Files.writeString(dir.resolve("providers.json"), """
                {"version":"1","providers":[{"key":"p3","name":"Idempotent Provider","sector":"MOBILE_PLAN","officialWebsite":"https://example.com","country":"FR"}]}
                """);
        Files.writeString(dir.resolve("mobile-offers.json"), """
                {"version":"1","offers":[{"providerKey":"p3","commercialName":"Idempotent Offer","sector":"MOBILE_PLAN","territory":"FR","pricing":{"amountMinor":999,"currency":"EUR","billingPeriod":"MONTHLY","taxesIncluded":true,"dataUnknown":false}}]}
                """);

        ensureActorUser();
        service.importDirectory(dir, true);
        long providersBefore = providerRepository.count();
        long offersBefore = offerRepository.count();

        ensureActorUser();
        var reports = service.importDirectory(dir, true);
        assertThat(providerRepository.count()).isEqualTo(providersBefore);
        assertThat(offerRepository.count()).isEqualTo(offersBefore);
        boolean hasExisting = reports.stream().flatMap(r -> r.actions().stream()).anyMatch(a -> a.startsWith("OFFER_EXISTING"));
        assertThat(hasExisting).isTrue();
    }

    @Test
    @DisplayName("Invalid sector produces an error")
    void invalidSectorReportsError() throws Exception {
        Path dir = Files.createTempDirectory("catalog-import-invalid");
        Files.writeString(dir.resolve("providers.json"), """
                {"version":"1","providers":[{"key":"p4","name":"Bad Provider","sector":"INVALID_SECTOR","officialWebsite":"https://example.com","country":"FR"}]}
                """);

        ensureActorUser();
        var reports = service.importDirectory(dir, true);
        assertThat(reports.get(0).errors()).isNotEmpty();
    }
}