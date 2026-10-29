package com.eparly.api;

import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferProviderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class PostgresIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OfferProviderRepository providerRepository;

	@Autowired
	private CommercialOfferRepository offerRepository;

	@Autowired
	private OfferEvidenceRepository evidenceRepository;

	@Test
	@DisplayName("Should successfully connect to PostgreSQL container via HikariCP")
	void shouldConnectToDatabase() throws SQLException {
		assertThat(dataSource).isNotNull();
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
		}
	}

	@Test
	@DisplayName("Should have executed Flyway V1 to V8 migrations in flyway_schema_history")
	void shouldHaveFlywayHistory() {
		Integer migrationCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
				Integer.class
		);
		assertThat(migrationCount).isNotNull().isEqualTo(1);
		Integer v3Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = true",
				Integer.class
		);
		assertThat(v3Count).isNotNull().isEqualTo(1);
		Integer v4Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = true",
				Integer.class
		);
		assertThat(v4Count).isNotNull().isEqualTo(1);
		Integer v5Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = true",
				Integer.class
		);
		assertThat(v5Count).isNotNull().isEqualTo(1);
		Integer v6Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = true",
				Integer.class
		);
		assertThat(v6Count).isNotNull().isEqualTo(1);
		Integer v7Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = true",
				Integer.class
		);
		assertThat(v7Count).isNotNull().isEqualTo(1);
		Integer v8Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = true",
				Integer.class
		);
		assertThat(v8Count).isNotNull().isEqualTo(1);
	}

	@Test
	@DisplayName("V19 adds controlled catalogue administration and preserves the pricing schema")
	void shouldApplyOfferPricingV19() {
		String latestVersion = jdbcTemplate.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
				String.class
		);
		assertThat(latestVersion).isEqualTo("19");

		Integer promotionPricingColumns = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'offer_pricing'
				  AND column_name IN ('promotion_price_type', 'promotion_valid_from',
				                      'promotion_valid_until', 'version')
				""", Integer.class);
		assertThat(promotionPricingColumns).isEqualTo(4);
		Integer pricingHistorySnapshots = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'offer_price_history'
				  AND column_name IN ('old_pricing_snapshot', 'new_pricing_snapshot')
				  AND udt_name = 'jsonb'
				""", Integer.class);
		assertThat(pricingHistorySnapshots).isEqualTo(2);
		Integer evidenceScope = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'offer_evidence'
				  AND column_name = 'evidence_scope'
				""", Integer.class);
		assertThat(evidenceScope).isEqualTo(1);
		Integer adminTables = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name IN ('catalog_admin_permissions', 'catalog_admin_audit')
				""", Integer.class);
		assertThat(adminTables).isEqualTo(2);
		Integer verificationTraceColumns = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'offer_evidence'
				  AND column_name IN ('verified_by_user_id', 'verified_pricing_version',
				                      'verified_provider_version', 'version')
				""", Integer.class);
		assertThat(verificationTraceColumns).isEqualTo(4);
		Integer auditDetailsType = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'catalog_admin_audit'
				  AND column_name = 'details' AND udt_name = 'jsonb'
				""", Integer.class);
		assertThat(auditDetailsType).isEqualTo(1);

		Integer nullableEvidenceDates = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'offer_evidence'
				  AND column_name IN ('consulted_at', 'verified_at')
				  AND is_nullable = 'YES'
				""", Integer.class);
		assertThat(nullableEvidenceDates).isEqualTo(2);

		Integer requiredEvidenceColumns = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'offer_evidence'
				  AND column_name IN ('offer_id', 'official_url', 'nature', 'verification_status', 'provenance')
				  AND is_nullable = 'NO'
				""", Integer.class);
		assertThat(requiredEvidenceColumns).isEqualTo(5);

		OfferProvider provider = new OfferProvider();
		provider.setName("V17 schema check " + UUID.randomUUID());
		provider.setSector(OfferSector.MOBILE_PLAN);
		provider.setOfficialWebsite("https://schema-check.example");
		provider = providerRepository.saveAndFlush(provider);

		CommercialOffer offer = new CommercialOffer();
		offer.setProvider(provider);
		offer.setCommercialName("V17 evidence check");
		offer.setSector(OfferSector.MOBILE_PLAN);
		offer = offerRepository.saveAndFlush(offer);

		OfferEvidence unknownDates = evidence(offer, null, null);
		unknownDates = evidenceRepository.saveAndFlush(unknownDates);
		assertThat(unknownDates.getConsultedAt()).isNull();
		assertThat(unknownDates.getVerifiedAt()).isNull();
		assertThat(jdbcTemplate.queryForObject("""
				SELECT consulted_at IS NULL AND verified_at IS NULL
				FROM offer_evidence WHERE id = ?
				""", Boolean.class, unknownDates.getId())).isTrue();

		Instant historicalConsultation = Instant.parse("2025-01-10T12:00:00Z");
		Instant historicalVerification = Instant.parse("2025-01-11T12:00:00Z");
		OfferEvidence historicalEvidence = evidence(offer, historicalConsultation, historicalVerification);
		historicalEvidence.setVerificationStatus(EvidenceStatus.VERIFIED);
		historicalEvidence = evidenceRepository.saveAndFlush(historicalEvidence);
		OfferEvidence reloaded = evidenceRepository.findById(historicalEvidence.getId()).orElseThrow();
		assertThat(reloaded.getConsultedAt()).isEqualTo(historicalConsultation);
		assertThat(reloaded.getVerifiedAt()).isEqualTo(historicalVerification);
	}

	private OfferEvidence evidence(CommercialOffer offer, Instant consultedAt, Instant verifiedAt) {
		OfferEvidence evidence = new OfferEvidence();
		evidence.setOffer(offer);
		evidence.setOfficialUrl("https://schema-check.example/offer");
		evidence.setConsultedAt(consultedAt);
		evidence.setVerifiedAt(verifiedAt);
		evidence.setNature("Schema migration verification");
		evidence.setVerificationStatus(EvidenceStatus.PENDING);
		evidence.setProvenance("Integration test");
		return evidence;
	}

	@Test
	@DisplayName("Should verify pgcrypto extension is installed by Flyway")
	void shouldVerifyPgcryptoExtension() {
		Integer extensionCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM pg_extension WHERE extname = 'pgcrypto'",
				Integer.class
		);
		assertThat(extensionCount).isNotNull().isEqualTo(1);
	}
}
