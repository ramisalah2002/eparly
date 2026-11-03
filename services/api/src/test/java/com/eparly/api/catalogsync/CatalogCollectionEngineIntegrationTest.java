package com.eparly.api.catalogsync;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.catalogsync.api.CatalogCandidateDraftRequest;
import com.eparly.api.catalogsync.connector.CreElectricityTariffConnector;
import com.eparly.api.catalogsync.connector.TradedoublerProductsConnector;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.orchestration.CatalogCandidateDraftService;
import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import com.eparly.api.catalogsync.scheduler.CatalogSyncScheduler;
import com.eparly.api.comparison.service.ComparisonService;
import com.eparly.api.offer.admin.service.CatalogAdminService;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.repository.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parcours complet collecte -> parsing -> normalisation -> validation -> deduplication -> persistance
 * candidate -> historique -> rapport, sur PostgreSQL isole et serveur HTTP local. Les extraits CRE sont
 * reels ; tous les flux JSON et variations de prix definis ici sont synthetiques.
 */
@SpringBootTest(properties = {
        "eparly.catalog-sync.max-retries=1",
        "eparly.catalog-sync.circuit-breaker-failure-threshold=2"
})
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class CatalogCollectionEngineIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CatalogSyncJobService jobService;
    @Autowired private CatalogCandidateDraftService draftService;
    @Autowired private CatalogAdminService catalogAdmin;
    @Autowired private ComparisonService comparisonService;
    @Autowired private UserRepository userRepository;
    @Autowired private CatalogSyncProperties properties;
    @Autowired private ApplicationContext applicationContext;

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        for (String table : List.of("catalog_observed_price_history", "catalog_source_offer_states",
                "catalog_offer_candidates", "catalog_sync_run_sources", "catalog_offer_observations",
                "catalog_offer_identities", "catalog_sync_runs", "catalog_sync_sources")) {
            jdbc.update("DELETE FROM " + table);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/robots.txt", exchange -> respond(exchange, 404, "text/plain", ""));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        properties.getTradedoubler().setEnabled(false);
        properties.getTradedoubler().setToken("");
    }

    @Test
    void schedulerStaysDisabledByDefaultAndTargetsThreeAmParis() {
        assertThat(applicationContext.getBeansOfType(CatalogSyncScheduler.class)).isEmpty();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getCron()).isEqualTo("0 0 3 * * *");
        assertThat(properties.getZone()).isEqualTo("Europe/Paris");
    }

    @Test
    void creFixtureTravelsTheWholePipelineWithoutTouchingThePublishedCatalog() throws Exception {
        int offersBefore = count("commercial_offers");
        int pricingBefore = count("offer_pricing");
        int publishedHistoryBefore = count("offer_price_history");
        int evidenceBefore = count("offer_evidence");
        serve("/Option_Base.csv", "text/csv", fixture("Option_Base"));
        UUID source = registerConnector(CreElectricityTariffConnector.KEY, "ELECTRICITY", "/Option_Base.csv", "{}");

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.status()).isEqualTo("SUCCESS");
        assertThat(run.id()).isNotNull();
        assertThat(run.durationMillis()).isNotNull().isNotNegative();
        assertThat(run.finishedAt()).isNotNull();
        assertThat(run.sourcesConfigured()).isEqualTo(1);
        assertThat(run.sourcesProcessed()).isEqualTo(1);
        assertThat(run.pagesFetched()).isEqualTo(1);
        assertThat(run.offersCollected()).isEqualTo(5);
        assertThat(run.offersDiscovered()).isEqualTo(5);
        assertThat(run.offersUpdated()).isZero();
        assertThat(run.offersDuplicates()).isZero();
        assertThat(run.offersRejected()).isZero();
        assertThat(run.errorsCount()).isZero();

        var perSource = jobService.runSources(run.id());
        assertThat(perSource).hasSize(1);
        assertThat(perSource.get(0).sourceId()).isEqualTo(source);
        assertThat(perSource.get(0).status()).isEqualTo("SUCCESS");
        assertThat(perSource.get(0).offersDiscovered()).isEqualTo(5);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_offer_candidates
                WHERE candidate_status = 'NEEDS_REVIEW' AND change_type = 'NEW_OFFER' AND sector = 'ELECTRICITY'
                """, Integer.class)).isEqualTo(5);
        var observation = jdbc.queryForMap("""
                SELECT price_kind, comparability_status, comparison_group, validation_status,
                       normalized_data->>'amountMinor' AS amount, normalized_data->>'billingPeriod' AS period,
                       normalized_data->'priceComponents'->>'PART_VARIABLE_TTC' AS kwh,
                       raw_payload->>'PART_FIXE_TTC' AS raw_fixed, validation_reasons::text AS reasons, run_id
                FROM catalog_offer_observations WHERE offer_external_key = 'TRV-ELEC:BASE:12'
                """);
        assertThat(observation.get("price_kind")).isEqualTo("VARIABLE");
        assertThat(observation.get("comparability_status")).isEqualTo("NOT_COMPARABLE");
        assertThat(observation.get("comparison_group")).isNull();
        assertThat(observation.get("validation_status")).isEqualTo("NEEDS_REVIEW");
        assertThat(observation.get("amount")).isEqualTo("28686");
        assertThat(observation.get("period")).isEqualTo("YEARLY");
        assertThat(observation.get("kwh")).isEqualTo("0.1985");
        assertThat(observation.get("raw_fixed")).isEqualTo("286,86");
        assertThat((String) observation.get("reasons")).contains("PRICE_DEPENDS_ON_USAGE");
        assertThat(observation.get("run_id")).isEqualTo(run.id());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_observed_price_history
                WHERE change_kind = 'FIRST_OBSERVED' AND old_amount_minor IS NULL
                """, Integer.class)).isEqualTo(5);

        assertThat(count("commercial_offers")).isEqualTo(offersBefore);
        assertThat(count("offer_pricing")).isEqualTo(pricingBefore);
        assertThat(count("offer_price_history")).isEqualTo(publishedHistoryBefore);
        assertThat(count("offer_evidence")).isEqualTo(evidenceBefore);
        assertThatThrownBy(() -> comparisonService.getCriteria(OfferSector.ELECTRICITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void relaunchingIsIdempotentAndUsesConditionalRequests() throws Exception {
        AtomicInteger notModified = new AtomicInteger();
        String body = fixture("Option_Base");
        server.createContext("/conditional.csv", exchange -> {
            String lastModified = "Wed, 26 Aug 2026 10:41:26 GMT";
            exchange.getResponseHeaders().set("Last-Modified", lastModified);
            if (lastModified.equals(exchange.getRequestHeaders().getFirst("If-Modified-Since"))) {
                notModified.incrementAndGet();
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
            } else {
                respond(exchange, 200, "text/csv", body);
            }
        });
        serve("/unconditional.csv", "text/csv", body);
        registerConnector(CreElectricityTariffConnector.KEY, "ELECTRICITY", "/conditional.csv", "{}");

        CatalogSyncReport first = jobService.runNow();
        CatalogSyncReport second = jobService.runNow();

        assertThat(first.offersDiscovered()).isEqualTo(5);
        assertThat(second.status()).isEqualTo("SUCCESS");
        assertThat(second.sourcesNotModified()).isEqualTo(1);
        assertThat(second.offersCollected()).isZero();
        assertThat(second.offersNotReturned()).isZero();
        assertThat(notModified).hasValue(1);
        assertThat(jobService.runSources(second.id()).get(0).status()).isEqualTo("NOT_MODIFIED");

        jdbc.update("DELETE FROM catalog_sync_run_sources");
        jdbc.update("UPDATE catalog_sync_sources SET collection_enabled = FALSE, permission_status = 'PENDING'");
        registerConnector(CreElectricityTariffConnector.KEY, "ELECTRICITY", "/unconditional.csv", "{}");
        int observations = count("catalog_offer_observations");

        CatalogSyncReport crossSource = jobService.runNow();
        CatalogSyncReport rerun = jobService.runNow();

        assertThat(crossSource.offersDuplicates()).isEqualTo(5);
        assertThat(rerun.offersUnchanged()).isEqualTo(5);
        assertThat(rerun.offersDiscovered()).isZero();
        assertThat(rerun.offersUpdated()).isZero();
        assertThat(count("catalog_offer_observations")).isEqualTo(observations + 5);
        assertThat(count("catalog_offer_candidates")).isEqualTo(5);
        assertThat(count("catalog_offer_identities")).isEqualTo(5);
    }

    @Test
    void priceChangesKeepTheHistoryAndAReturnToAFormerPriceIsDetected() {
        AtomicReference<String> feed = new AtomicReference<>(jsonFeed(offer("plan", "Forfait 20 Go", "9,99", "")));
        server.createContext("/feed.json", exchange -> respond(exchange, 200, "application/json", feed.get()));
        registerGeneric("JSON_FEED", "MOBILE_PLAN", "/feed.json");

        assertThat(jobService.runNow().offersDiscovered()).isEqualTo(1);

        feed.set(jsonFeed(offer("plan", "Forfait 20 Go", "12,99", "")));
        assertThat(jobService.runNow().offersUpdated()).isEqualTo(1);
        assertThat(candidatesWithStatus("SUPERSEDED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT change_type FROM catalog_offer_candidates WHERE candidate_status = 'NEEDS_REVIEW'
                """, String.class)).isEqualTo("PRICE_CHANGED");

        feed.set(jsonFeed(offer("plan", "Forfait 20 Go", "9,99", "")));
        CatalogSyncReport back = jobService.runNow();

        assertThat(back.offersUpdated()).isEqualTo(1);
        assertThat(back.offersUnchanged()).isZero();
        assertThat(count("catalog_offer_observations")).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT old_amount_minor FROM catalog_observed_price_history ORDER BY detected_at, id
                """, Long.class)).hasSize(3).contains(999L, 1299L);
        assertThat(candidatesWithStatus("NEEDS_REVIEW")).isEqualTo(1);
        assertThat(count("catalog_offer_candidates")).isEqualTo(3);

        feed.set(jsonFeed(offer("plan", "Forfait 20 Go", "9,99",
                "\"promotionPrice\":\"4,99\",\"promotionValidUntil\":\"2099-01-31\"")));
        jobService.runNow();
        assertThat(jdbc.queryForObject("""
                SELECT change_type FROM catalog_offer_candidates WHERE candidate_status = 'NEEDS_REVIEW'
                """, String.class)).isEqualTo("PROMOTION_CHANGED");
    }

    @Test
    void missingPriceNeverBecomesZeroAndStaysACandidateWithItsReason() {
        serve("/feed.json", "application/json", jsonFeed("""
                {"id":"none","name":"Sans prix","url":"https://127.0.0.1/none","eligibility":"Tous",
                 "currency":"EUR","billingPeriod":"MONTHLY"},
                {"id":"bad","url":"https://127.0.0.1/bad","price":"5 EUR"}
                """));
        registerGeneric("JSON_FEED", "INSURANCE", "/feed.json");

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.offersDiscovered()).isEqualTo(1);
        assertThat(run.offersRejected()).isEqualTo(1);
        var row = jdbc.queryForMap("""
                SELECT price_kind, normalized_data->'amountMinor' AS amount, validation_reasons::text AS reasons,
                       comparability_status
                FROM catalog_offer_observations WHERE offer_external_key = 'none'
                """);
        assertThat(row.get("price_kind")).isEqualTo("MISSING");
        assertThat(row.get("amount").toString()).isEqualTo("null");
        assertThat((String) row.get("reasons")).contains("PRICE_MISSING_OR_AMBIGUOUS",
                "COMPARABILITY_ATTRIBUTE_MISSING:guarantees");
        assertThat(row.get("comparability_status")).isEqualTo("INSUFFICIENT_ATTRIBUTES");
        assertThat(jdbc.queryForObject("""
                SELECT new_amount_minor FROM catalog_observed_price_history WHERE offer_external_key = 'none'
                """, Long.class)).isNull();
        assertThat(candidatesWithStatus("NEEDS_REVIEW")).isEqualTo(1);
    }

    @Test
    void offerAbsentFromASourceIsOnlyNotedAndAnUnavailableSourceProvesNothing() {
        AtomicReference<String> feed = new AtomicReference<>(jsonFeed(
                offer("a", "Offre A", "5", "") + "," + offer("b", "Offre B", "6", "")));
        AtomicInteger status = new AtomicInteger(200);
        server.createContext("/feed.json", exchange -> respond(exchange, status.get(), "application/json", feed.get()));
        registerGeneric("JSON_FEED", "PRESS", "/feed.json");
        jobService.runNow();
        int candidates = count("catalog_offer_candidates");

        status.set(503);
        CatalogSyncReport unavailable = jobService.runNow();
        assertThat(unavailable.status()).isEqualTo("FAILED");
        assertThat(unavailable.offersNotReturned()).isZero();
        assertThat(missingOffers()).isZero();

        status.set(200);
        feed.set(jsonFeed(offer("a", "Offre A", "5", "")));
        CatalogSyncReport absent = jobService.runNow();
        assertThat(absent.status()).isEqualTo("SUCCESS");
        assertThat(absent.offersNotReturned()).isEqualTo(1);
        assertThat(missingOffers()).isEqualTo(1);
        assertThat(count("catalog_offer_candidates")).isEqualTo(candidates);
        assertThat(candidatesWithStatus("NEEDS_REVIEW")).isEqualTo(candidates);

        feed.set("{\"offers\":[]}");
        CatalogSyncReport empty = jobService.runNow();
        assertThat(empty.offersNotReturned()).isZero();
        assertThat(missingOffers()).isEqualTo(1);

        feed.set(jsonFeed(offer("a", "Offre A", "5", "") + "," + offer("b", "Offre B", "6", "")));
        assertThat(jobService.runNow().offersNotReturned()).isZero();
        assertThat(missingOffers()).isZero();
    }

    @Test
    void sameOfferFromTwoSourcesIsDeduplicatedAndADisagreementGoesToReview() {
        String shared = """
                {"id":"%s","name":"Offre Cloud 2 To","providerName":"%s","url":"https://127.0.0.1/%s",
                 "price":"%s","currency":"EUR","billingPeriod":"MONTHLY","eligibility":"Tous"}
                """;
        serve("/a.json", "application/json", jsonFeed(shared.formatted("a-1", "Stockage Plus SAS", "a", "9,99")
                + "," + shared.formatted("a-1", "Stockage Plus SAS", "a", "9,99")));
        serve("/b.json", "application/json", jsonFeed(shared.formatted("b-1", "stockage plus", "b", "9,99")));
        serve("/c.json", "application/json", jsonFeed(shared.formatted("c-1", "STOCKAGE PLUS", "c", "7,99")));
        registerGeneric("JSON_FEED", "CLOUD_STORAGE", "/a.json");
        CatalogSyncReport first = jobService.runNow();
        assertThat(first.offersDiscovered()).isEqualTo(1);
        assertThat(first.offersDuplicates()).as("doublon interne au flux").isEqualTo(1);

        registerGeneric("JSON_FEED", "CLOUD_STORAGE", "/b.json");
        CatalogSyncReport second = jobService.runNow();
        assertThat(second.offersDuplicates()).isEqualTo(2);
        assertThat(second.offersDiscovered()).isZero();
        assertThat(count("catalog_offer_candidates")).isEqualTo(1);

        registerGeneric("JSON_FEED", "CLOUD_STORAGE", "/c.json");
        jobService.runNow();
        assertThat(count("catalog_offer_identities")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT change_type FROM catalog_offer_candidates", String.class))
                .containsExactlyInAnyOrder("NEW_OFFER", "SOURCE_CONFLICT");
    }

    @Test
    void multipleCategoriesCoexistInOneFeedWithoutBeingMixed() {
        serve("/multi.json", "application/json", jsonFeed("""
                {"id":"m","name":"Forfait","sector":"MOBILE_PLAN","url":"https://127.0.0.1/m","price":"10 €",
                 "currency":"EUR","billingPeriod":"MONTHLY","commitmentMonths":0,
                 "characteristics":{"dataGb":20,"network5G":true}},
                {"id":"g","name":"Forfait","sector":"GAS","url":"https://127.0.0.1/g","price":"10 €",
                 "currency":"EUR","billingPeriod":"MONTHLY"},
                {"id":"x","name":"Forfait","sector":"INCONNU","url":"https://127.0.0.1/x","price":"10 €",
                 "currency":"EUR","billingPeriod":"MONTHLY"}
                """));
        registerGeneric("JSON_FEED", "OTHER_RECURRING", "/multi.json");

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.offersDiscovered()).isEqualTo(3);
        assertThat(run.offersDuplicates()).isZero();
        assertThat(jdbc.queryForList("SELECT sector FROM catalog_offer_candidates", String.class))
                .containsExactlyInAnyOrder("MOBILE_PLAN", "GAS", "OTHER_RECURRING");
        assertThat(jdbc.queryForObject("""
                SELECT comparability_status FROM catalog_offer_observations WHERE offer_external_key = 'm'
                """, String.class)).isEqualTo("COMPARABLE_WITHIN_GROUP");
        assertThat(jdbc.queryForObject("""
                SELECT comparability_status FROM catalog_offer_observations WHERE offer_external_key = 'x'
                """, String.class)).isEqualTo("NOT_COMPARABLE");
    }

    @Test
    void failingSourcesAreIsolatedClassifiedAndEventuallyCircuitBroken() throws Exception {
        serve("/ok.json", "application/json", jsonFeed(offer("ok", "Offre", "5", "")));
        server.createContext("/invalid.json", exchange -> respond(exchange, 200, "application/json", "{not json"));
        server.createContext("/limited.json", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        UUID ok = registerGeneric("JSON_FEED", "TRANSPORT", "/ok.json");
        UUID invalid = registerGeneric("JSON_FEED", "TRANSPORT", "/invalid.json");
        UUID limited = registerGeneric("JSON_FEED", "TRANSPORT", "/limited.json");
        serve("/ok.csv", "text/csv", "a;b");
        UUID csv = registerGeneric("CSV_FEED", "TRANSPORT", "/ok.csv");

        CatalogSyncReport first = jobService.runNow();

        assertThat(first.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(first.sourcesProcessed()).isEqualTo(4);
        assertThat(first.sourcesFailed()).isEqualTo(3);
        assertThat(first.errorsCount()).isEqualTo(3);
        assertThat(first.offersDiscovered()).isEqualTo(1);
        assertThat(errorCodeOf(first, limited)).isEqualTo("HTTP_RATE_LIMITED");
        assertThat(errorCodeOf(first, invalid)).isEqualTo("INVALID_SOURCE_OR_DATA");
        assertThat(errorCodeOf(first, csv)).isEqualTo("UNSUPPORTED_SOURCE");
        assertThat(errorCodeOf(first, ok)).isNull();
        assertThat(jdbc.queryForObject("SELECT last_error FROM catalog_sync_sources WHERE id = ?",
                String.class, limited)).contains("429");

        CatalogSyncReport second = jobService.runNow();
        assertThat(second.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(jdbc.queryForObject("SELECT consecutive_failures FROM catalog_sync_sources WHERE id = ?",
                Integer.class, limited)).isEqualTo(2);

        CatalogSyncReport third = jobService.runNow();
        assertThat(third.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(third.sourcesSkipped()).isEqualTo(3);
        assertThat(third.sourcesProcessed()).isEqualTo(1);
        var limitedReport = jobService.runSources(third.id()).stream()
                .filter(report -> report.sourceId().equals(limited)).findFirst().orElseThrow();
        assertThat(limitedReport.status()).isEqualTo("SKIPPED_CIRCUIT_OPEN");
        assertThat(jdbc.queryForObject("""
                SELECT circuit_open_until IS NULL AND consecutive_failures = 0
                FROM catalog_sync_sources WHERE id = ?
                """, Boolean.class, ok)).isTrue();
    }

    @Test
    void unknownConnectorFailsOnlyItsSource() {
        serve("/ok.json", "application/json", jsonFeed(offer("ok", "Offre", "5", "")));
        registerGeneric("JSON_FEED", "BANKING", "/ok.json");
        UUID unknown = registerConnector("awin-telco", "BANKING", "/ok2.json", "{}");

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(errorCodeOf(run, unknown)).isEqualTo("INVALID_SOURCE_OR_DATA");
    }

    @Test
    void concurrentExecutionIsSkippedByTheDatabaseLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/blocking.json", exchange -> {
            entered.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "application/json", jsonFeed(offer("x", "Offre", "5", "")));
        });
        properties.setRequestTimeout(java.time.Duration.ofSeconds(30));
        registerGeneric("JSON_FEED", "SPORT_FITNESS", "/blocking.json");

        CompletableFuture<CatalogSyncReport> running = CompletableFuture.supplyAsync(jobService::runNow);
        assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
        CatalogSyncReport skipped = jobService.runNow();
        release.countDown();
        CatalogSyncReport finished = running.get(30, TimeUnit.SECONDS);

        assertThat(skipped.status()).isEqualTo("SKIPPED");
        assertThat(finished.status()).isEqualTo("SUCCESS");
        assertThat(finished.offersDiscovered()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sync_runs WHERE status = 'SKIPPED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void runLeftRunningByACrashIsClosedByTheNextRun() {
        jdbc.update("INSERT INTO catalog_sync_runs(status) VALUES ('RUNNING')");

        assertThat(jobService.runNow().status()).isEqualTo("SUCCESS");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sync_runs WHERE status = 'RUNNING'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sync_runs WHERE status = 'FAILED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void tradedoublerStaysInertWithoutTokenThenPaginatesWithoutLeakingIt() {
        List<String> queries = new ArrayList<>();
        AtomicInteger status = new AtomicInteger(200);
        // Forme de reponse documentee par https://dev.tradedoubler.com/products/publisher/ ; valeurs synthetiques.
        server.createContext("/1.0/products.json", exchange -> {
            queries.add(exchange.getRequestURI().toString());
            boolean firstPage = exchange.getRequestURI().getPath().contains(";page=0;");
            StringBuilder products = new StringBuilder();
            int count = firstPage ? 100 : 20;
            int offset = firstPage ? 0 : 100;
            for (int index = offset; index < offset + count; index++) {
                if (index > offset) products.append(',');
                products.append(tradedoublerProduct(index));
            }
            respond(exchange, status.get(), "application/json",
                    "{\"productHeader\":{\"totalHits\":120},\"products\":[" + products + "]}");
        });
        UUID source = registerConnector(TradedoublerProductsConnector.KEY, "OTHER_RECURRING", "/1.0/products.json",
                "{\"feedId\":\"123\",\"merchantDomains\":[\"merchant.example.fr\"]}");

        CatalogSyncReport inert = jobService.runNow();
        assertThat(inert.status()).isEqualTo("SUCCESS");
        assertThat(inert.sourcesSkipped()).isEqualTo(1);
        assertThat(jobService.runSources(inert.id()).get(0).status()).isEqualTo("SKIPPED_NOT_CONFIGURED");
        assertThat(queries).isEmpty();

        String token = "test-token-not-a-real-credential";
        properties.getTradedoubler().setEnabled(true);
        properties.getTradedoubler().setToken(token);

        CatalogSyncReport run = jobService.runNow();

        assertThat(run.status()).isEqualTo("SUCCESS");
        assertThat(run.pagesFetched()).isEqualTo(2);
        assertThat(run.offersDiscovered()).isEqualTo(120);
        assertThat(queries).hasSize(2);
        assertThat(queries.get(0)).contains(";fid=123;page=0;pageSize=100;limit=100?token=" + token);
        assertThat(queries.get(1)).contains(";page=1;pageSize=100;limit=200?token=");
        var row = jdbc.queryForMap("""
                SELECT canonical_url, normalized_data->>'amountMinor' AS amount, validation_status,
                       validation_reasons::text AS reasons
                FROM catalog_offer_observations WHERE offer_external_key = 'TD:123:P5'
                """);
        assertThat(row.get("canonical_url")).isEqualTo("https://merchant.example.fr/p/5");
        assertThat(row.get("amount")).isEqualTo("1299");
        assertThat((String) row.get("reasons")).contains("BILLING_PERIOD_UNKNOWN");

        status.set(403);
        CatalogSyncReport denied = jobService.runNow();
        assertThat(denied.status()).isEqualTo("FAILED");
        assertThat(errorCodeOf(denied, source)).isEqualTo("HTTP_CLIENT_ERROR");
        for (String table : List.of("catalog_sync_runs", "catalog_sync_run_sources", "catalog_sync_sources",
                "catalog_offer_observations", "catalog_offer_candidates")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " t WHERE t::text LIKE ?",
                    Integer.class, "%" + token + "%")).as(table).isZero();
        }
    }

    @Test
    void onlyAHumanTurnsACandidateIntoADraftWithPendingEvidenceThroughTheAdminRules() throws Exception {
        User admin = new User("collect-" + UUID.randomUUID() + "@eparly.test", "irrelevant-hash");
        admin = userRepository.saveAndFlush(admin);
        UUID actor = admin.getId();
        var provider = catalogAdmin.createProvider(actor, "Fournisseur Reglemente " + UUID.randomUUID(),
                OfferSector.ELECTRICITY, "https://fournisseur.example.fr", "FR");
        serve("/Option_Base.csv", "text/csv", fixture("Option_Base"));
        registerConnector(CreElectricityTariffConnector.KEY, "ELECTRICITY", "/Option_Base.csv", "{}");
        jobService.runNow();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM commercial_offers WHERE provider_id = ?
                """, Integer.class, provider.id())).isZero();
        UUID candidate = jdbc.queryForObject("""
                SELECT candidate.id FROM catalog_offer_candidates candidate
                JOIN catalog_offer_observations observation ON observation.id = candidate.latest_observation_id
                WHERE observation.offer_external_key = 'TRV-ELEC:BASE:6'
                """, UUID.class);

        // Regle du catalogue conservee : la preuve doit appartenir au domaine du fournisseur.
        assertThatThrownBy(() -> draftService.createDraft(candidate,
                new CatalogCandidateDraftRequest(provider.id(), null, null, null), actor))
                .isInstanceOf(ApiException.class).hasMessageContaining("official HTTPS domain");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commercial_offers WHERE provider_id = ?",
                Integer.class, provider.id())).as("annulation complete").isZero();

        var draft = draftService.createDraft(candidate, new CatalogCandidateDraftRequest(provider.id(), null,
                "https://fournisseur.example.fr/tarifs", null), actor);

        assertThat(draft.publicationStatus().name()).isEqualTo("DRAFT");
        var pricing = jdbc.queryForMap("""
                SELECT amount_minor, billing_period, data_unknown FROM offer_pricing WHERE offer_id = ?
                """, draft.id());
        assertThat(pricing.get("amount_minor")).isEqualTo(19081L);
        assertThat(pricing.get("billing_period")).isEqualTo("YEARLY");
        assertThat(pricing.get("data_unknown")).isEqualTo(true);
        assertThat(jdbc.queryForObject("""
                SELECT verification_status FROM offer_evidence WHERE offer_id = ?
                """, String.class, draft.id())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                SELECT candidate_status FROM catalog_offer_candidates WHERE id = ?
                """, String.class, candidate)).isEqualTo("DRAFT_CREATED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_admin_audit WHERE resource_id = ? AND action = 'CANDIDATE_DRAFT_CREATED'
                """, Integer.class, candidate)).isEqualTo(1);

        assertThatThrownBy(() -> catalogAdmin.publish(actor, draft.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> draftService.createDraft(candidate, new CatalogCandidateDraftRequest(provider.id(),
                "Autre", "https://fournisseur.example.fr/tarifs", null), actor))
                .isInstanceOf(ApiException.class).hasMessageContaining("awaiting review");
    }

    private String errorCodeOf(CatalogSyncReport run, UUID source) {
        return jobService.runSources(run.id()).stream().filter(report -> report.sourceId().equals(source))
                .findFirst().orElseThrow().errorCode();
    }

    private UUID registerGeneric(String type, String sector, String path) {
        return register(type, sector, path, "{}", null);
    }

    private UUID registerConnector(String key, String sector, String path, String config) {
        return register("OFFICIAL_API", sector, path, config, key);
    }

    private UUID register(String type, String sector, String path, String config, String connectorKey) {
        return jdbc.queryForObject("""
                INSERT INTO catalog_sync_sources
                    (source_name, official_domain, source_url, source_type, sector, country, collection_enabled,
                     permission_status, permission_reason, permission_updated_at, extractor_config, connector_key)
                VALUES (?, '127.0.0.1', ?, ?, ?, 'FR', TRUE, 'AUTHORIZED', 'Integration test',
                        CURRENT_TIMESTAMP, CAST(? AS jsonb), ?)
                RETURNING id
                """, UUID.class, "Fixture " + path, baseUrl() + path, type, sector, config, connectorKey);
    }

    private void serve(String path, String contentType, String body) {
        server.createContext(path, exchange -> respond(exchange, 200, contentType, body));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private int candidatesWithStatus(String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalog_offer_candidates WHERE candidate_status = ?",
                Integer.class, status);
    }

    private int missingOffers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalog_source_offer_states WHERE missing_since IS NOT NULL",
                Integer.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String jsonFeed(String items) {
        return "{\"offers\":[" + items + "]}";
    }

    private static String offer(String id, String name, String price, String extra) {
        return """
                {"id":"%s","name":"%s","url":"https://127.0.0.1/%s","price":"%s €","currency":"EUR",
                 "billingPeriod":"MONTHLY","eligibility":"Tous"%s}
                """.formatted(id, name, id, price, extra.isEmpty() ? "" : "," + extra);
    }

    private static String tradedoublerProduct(int index) {
        return """
                {"name":"Produit %d","description":"Description","offers":[{"feedId":123,
                 "sourceProductId":"P%d","sourceProductUrl":"https://merchant.example.fr/p/%d","id":"o%d",
                 "programName":"Programme","modified":1,"priceHistory":[
                   {"price":{"value":"9.99","currency":"EUR"},"date":1},
                   {"price":{"value":"12.99","currency":"EUR"},"date":2}]}]}
                """.formatted(index, index, index, index);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = CatalogCollectionEngineIntegrationTest.class
                .getResourceAsStream("/catalog-fixtures/cre/" + name + ".excerpt.csv")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
        if (response.length == 0) {
            exchange.close();
            return;
        }
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
