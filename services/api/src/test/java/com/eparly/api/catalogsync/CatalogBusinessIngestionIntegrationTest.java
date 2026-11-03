package com.eparly.api.catalogsync;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.catalogsync.connector.OpenTariffFeedConnectors;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration aux tables metier sur PostgreSQL isole. Toutes les donnees de ce test sont synthetiques. */
@SpringBootTest(properties = "eparly.catalog-sync.business-ingestion-enabled=true")
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class CatalogBusinessIngestionIntegrationTest {
    private static final String HEADER = "enseigne,formule,prix_par_periode_eur,periodicite_facturation,"
            + "cout_annuel_hors_frais_eur,perimetre_prix,frais_inscription_eur,engagement_mois,sans_engagement,"
            + "preavis_resiliation,canal_resiliation,acces_multi_clubs,nombre_clubs_declare,date_releve,url_source,remarque\n";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CatalogSyncJobService jobService;

    private HttpServer server;
    private final AtomicReference<String> feed = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);

    @BeforeEach
    void setUp() throws Exception {
        for (String sql : new String[]{"DELETE FROM catalog_observed_price_history",
                "DELETE FROM catalog_source_offer_states", "DELETE FROM catalog_offer_candidates",
                "DELETE FROM catalog_sync_run_sources", "DELETE FROM catalog_offer_observations",
                "DELETE FROM catalog_offer_identities", "DELETE FROM catalog_sync_runs",
                "DELETE FROM catalog_sync_sources", "DELETE FROM commercial_offers", "DELETE FROM offer_providers"}) {
            jdbc.update(sql);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/gym.csv", exchange -> {
            byte[] body = feed.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        jdbc.update("""
                INSERT INTO catalog_sync_sources
                    (source_name, official_domain, source_url, source_type, sector, country, collection_enabled,
                     permission_status, permission_reason, permission_updated_at, connector_key)
                VALUES ('Fixture', '127.0.0.1', ?, 'CSV_FEED', 'SPORT_FITNESS', 'FR', TRUE, 'AUTHORIZED', 'Test',
                        CURRENT_TIMESTAMP, ?)
                """, "http://127.0.0.1:" + server.getAddress().getPort() + "/gym.csv",
                OpenTariffFeedConnectors.GYM_OBSERVATORY);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createsDraftCatalogRowsIdempotentlyAndHistorizesPriceChanges() {
        feed.set(HEADER + row("Club Test", "Mensuel", "300"));

        CatalogSyncReport first = jobService.runNow();

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(first.providersCreated()).isEqualTo(1);
        assertThat(first.catalogOffersCreated()).isEqualTo(1);
        assertThat(first.catalogEvidenceAdded()).isEqualTo(1);
        var offer = jdbc.queryForMap("""
                SELECT o.id, o.publication_status, o.sector, o.commitment, p.amount_minor, p.billing_period,
                       p.activation_fee_minor, p.data_unknown, e.verification_status, e.official_url, e.verified_at
                FROM commercial_offers o JOIN offer_pricing p ON p.offer_id = o.id JOIN offer_evidence e ON e.offer_id = o.id
                WHERE o.commercial_name = 'Club Test Mensuel'
                """);
        assertThat(offer.get("publication_status")).isEqualTo("DRAFT");
        assertThat(offer.get("sector")).isEqualTo("SPORT_FITNESS");
        assertThat(offer.get("commitment")).isEqualTo("12 mois");
        assertThat(offer.get("amount_minor")).isEqualTo(30000L);
        assertThat(offer.get("billing_period")).isEqualTo("YEARLY");
        assertThat(offer.get("activation_fee_minor")).isEqualTo(1999L);
        assertThat(offer.get("data_unknown")).isEqualTo(true);
        assertThat(offer.get("verification_status")).isEqualTo("PENDING");
        assertThat(offer.get("verified_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_offer_candidates WHERE candidate_status = 'DRAFT_CREATED'",
                Integer.class)).isEqualTo(1);

        CatalogSyncReport second = jobService.runNow();
        assertThat(second.catalogOffersCreated()).isZero();
        assertThat(second.catalogPricesChanged()).isZero();
        assertThat(second.catalogEvidenceAdded()).isZero();
        assertThat(second.offersUnchanged()).isEqualTo(1);
        assertThat(count("commercial_offers")).isEqualTo(1);
        assertThat(count("offer_evidence")).isEqualTo(1);
        assertThat(count("offer_price_history")).isEqualTo(1);

        feed.set(HEADER + row("Club Test", "Mensuel", "360"));
        CatalogSyncReport third = jobService.runNow();
        assertThat(third.catalogPricesChanged()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_minor FROM offer_pricing", Long.class)).isEqualTo(36000L);
        assertThat(jdbc.queryForObject("""
                SELECT old_amount_minor FROM offer_price_history WHERE new_amount_minor = 36000
                """, Long.class)).isEqualTo(30000L);
    }

    @Test
    void validatedAndPreexistingOffersAreNeverOverwritten() {
        UUID provider = jdbc.queryForObject("""
                INSERT INTO offer_providers (name, sector, official_website) VALUES ('Club Test', 'SPORT_FITNESS', 'https://a.example.fr')
                RETURNING id
                """, UUID.class);
        UUID preexisting = jdbc.queryForObject("""
                INSERT INTO commercial_offers (provider_id, commercial_name, sector) VALUES (?, 'Club Test Mensuel', 'SPORT_FITNESS')
                RETURNING id
                """, UUID.class, provider);
        feed.set(HEADER + row("Club Test", "Mensuel", "300") + row("Club Test", "Annuel", "500"));

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.providersCreated()).isZero();
        assertThat(run.catalogOffersCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offer_pricing WHERE offer_id = ?", Integer.class, preexisting))
                .isZero();
        assertThat(jdbc.queryForObject("""
                SELECT business_block_reason FROM catalog_offer_candidates WHERE existing_offer_id = ?
                """, String.class, preexisting)).isEqualTo("EXISTING_OFFER_NOT_MANAGED_BY_BATCH");

        jdbc.update("UPDATE commercial_offers SET publication_status = 'VERIFIED' WHERE commercial_name = 'Club Test Annuel'");
        feed.set(HEADER + row("Club Test", "Mensuel", "300") + row("Club Test", "Annuel", "999"));

        CatalogSyncReport changed = jobService.runNow();

        assertThat(changed.catalogPricesChanged()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT p.amount_minor FROM offer_pricing p JOIN commercial_offers o ON o.id = p.offer_id
                WHERE o.commercial_name = 'Club Test Annuel'
                """, Long.class)).isEqualTo(50000L);
        assertThat(jdbc.queryForObject("""
                SELECT business_block_reason FROM catalog_offer_candidates
                WHERE candidate_status = 'NEEDS_REVIEW' AND change_type = 'PRICE_CHANGED'
                """, String.class)).isEqualTo("VALIDATED_OFFER_REQUIRES_REVIEW");
    }

    @Test
    void incompleteOfferStaysCandidateWithAPreciseReasonAndOutageDeletesNothing() {
        feed.set(HEADER + row("Club Test", "Sans prix", "") + row("Club Test", "Complet", "300"));

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.catalogOffersCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT business_block_reason FROM catalog_offer_candidates WHERE candidate_status = 'NEEDS_REVIEW'
                """, String.class)).isEqualTo("PRICE_UNKNOWN");

        status.set(404);
        feed.set("");
        CatalogSyncReport outage = jobService.runNow();
        assertThat(outage.status()).isEqualTo("FAILED");
        assertThat(count("commercial_offers")).isEqualTo(1);
    }

    @Test
    void referenceProtectionDoesNotDependOnJsonFormatting() {
        UUID provider = jdbc.queryForObject("""
                INSERT INTO offer_providers (name, sector, official_website)
                VALUES ('Reference', 'ELECTRICITY', 'https://a.example.fr') RETURNING id
                """, UUID.class);
        UUID offer = jdbc.queryForObject("""
                INSERT INTO commercial_offers (provider_id, commercial_name, sector, characteristics, reference_kind)
                VALUES (?, 'Tarif', 'ELECTRICITY', '{ "offerNature" : "AUTRE" }', 'REFERENCE_REGULATED_TARIFF')
                RETURNING id
                """, UUID.class, provider);

        assertThatThrownBy(() -> jdbc.update("UPDATE commercial_offers SET publication_status = 'VERIFIED' WHERE id = ?", offer))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void dailyCronFiresAtThreeParisAcrossDaylightSavingChanges() {
        CronExpression cron = CronExpression.parse("0 0 3 * * *");
        ZoneId paris = ZoneId.of("Europe/Paris");

        ZonedDateTime spring = cron.next(ZonedDateTime.of(2027, 3, 28, 1, 0, 0, 0, paris));
        ZonedDateTime autumn = cron.next(ZonedDateTime.of(2027, 10, 31, 1, 0, 0, 0, paris));

        assertThat(spring.getHour()).isEqualTo(3);
        assertThat(autumn.getHour()).isEqualTo(3);
        assertThat(cron.next(autumn).getDayOfMonth()).isEqualTo(1);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String row(String brand, String plan, String annual) {
        return brand + "," + plan + ",25,4 semaines," + annual + ",France,19.99,12,non,4 semaines,en club,oui,10,2026-09-28,"
                + "https://club.example.fr/prix,\n";
    }
}
