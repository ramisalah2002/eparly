package com.eparly.api.catalogsync;

import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.catalogsync.fetch.PinnedDnsResolver;
import com.eparly.api.catalogsync.normalization.PriceNormalizationService;
import com.eparly.api.catalogsync.extraction.HtmlCatalogOfferExtractor;
import com.eparly.api.catalogsync.extraction.JsonCatalogOfferExtractor;
import com.eparly.api.catalogsync.discovery.SitemapDiscoveryService;
import com.eparly.api.catalogsync.scheduler.CatalogSyncScheduler;
import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.catalogsync.validation.SourceTrustPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class CatalogSyncIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CatalogSyncJobService jobService;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private OfficialSourceHttpClient httpClient;
    @Autowired private PinnedDnsResolver pinnedDnsResolver;
    @Autowired private SourceTrustPolicy sourceTrustPolicy;
    @Autowired private JsonCatalogOfferExtractor jsonExtractor;
    @Autowired private HtmlCatalogOfferExtractor htmlExtractor;
    @Autowired private SitemapDiscoveryService sitemapDiscovery;
    @Autowired private PriceNormalizationService priceNormalization;

    private HttpServer server;

    @Test
    void schedulerIsNotRegisteredAndEmptyManualRunIsAValidNoOp() {
        assertThat(applicationContext.getBeansOfType(CatalogSyncScheduler.class)).isEmpty();
        var emptyRun = jobService.runNow();
        assertThat(emptyRun.status()).isEqualTo("SUCCESS");
        assertThat(emptyRun.sourcesConfigured()).isZero();
        assertThat(emptyRun.pagesFetched()).isZero();
    }

    @Test
    void collectorHonorsRetryAfterRejectsForeignRedirectsAndBlocksPrivateNetworkTargets() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> respond(exchange, 404, "text/plain", ""));
        server.createContext("/rate-limited.json", exchange -> {
            if (requests.getAndIncrement() == 0) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
                exchange.close();
            } else {
                respond(exchange, 200, "application/json", "{\"offers\":[]}");
            }
        });
        server.createContext("/foreign-redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://localhost:"
                    + server.getAddress().getPort() + "/offers.json");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        CatalogSyncSource source = new CatalogSyncSource(UUID.randomUUID(), null, "HTTP fixture",
                "127.0.0.1", baseUrl() + "/rate-limited.json", CatalogSourceType.JSON_FEED,
                OfferSector.STREAMING, true, "AUTHORIZED", null, null, "{}");

        assertThat(httpClient.fetch(source, source.sourceUrl(), null, null).statusCode()).isEqualTo(200);
        assertThat(requests).hasValue(2);
        assertThatThrownBy(() -> httpClient.fetch(source, baseUrl() + "/foreign-redirect", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the configured official domain");

        CatalogSyncSource privateTarget = new CatalogSyncSource(source.id(), null, "Private fixture",
                "169.254.169.254", "https://169.254.169.254/latest/meta-data",
                CatalogSourceType.JSON_FEED, OfferSector.STREAMING, true, "AUTHORIZED", null, null, "{}");
        assertThatThrownBy(() -> sourceTrustPolicy.validate(privateTarget, privateTarget.sourceUrl()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public network address");
        assertThatThrownBy(() -> pinnedDnsResolver.resolve("169.254.169.254"))
                .isInstanceOf(java.net.UnknownHostException.class)
                .hasMessageContaining("non-public network address");
        var publicAddress = java.net.InetAddress.getByName("8.8.8.8");
        var rebindingAddress = java.net.InetAddress.getByName("100.100.100.200");
        assertThatThrownBy(() -> sourceTrustPolicy.validateResolvedAddresses("example.com",
                new java.net.InetAddress[]{publicAddress, rebindingAddress}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public network address");
        var documentationIpv6 = java.net.InetAddress.getByName("2001:db8::1");
        assertThatThrownBy(() -> sourceTrustPolicy.validateResolvedAddresses("example.com",
                new java.net.InetAddress[]{documentationIpv6}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public network address");
        var documentationRangeIpv6 = java.net.InetAddress.getByName("3fff::1");
        assertThatThrownBy(() -> sourceTrustPolicy.validateResolvedAddresses("example.com",
                new java.net.InetAddress[]{documentationRangeIpv6}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public network address");

        CatalogSyncSource nonstandardPort = new CatalogSyncSource(source.id(), null, "Port fixture",
                "example.com", "https://example.com:8443/offers", CatalogSourceType.JSON_FEED,
                OfferSector.STREAMING, true, "AUTHORIZED", null, null, "{}");
        assertThatThrownBy(() -> sourceTrustPolicy.validate(nonstandardPort, nonstandardPort.sourceUrl()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standard HTTPS port");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void authorizedLocalSourcesFlowThroughFetchExtractionNormalizationAndIdempotentCandidates() throws Exception {
        AtomicReference<String> feed = new AtomicReference<>(feed("9,99 € par mois"));
        AtomicBoolean blockedRequested = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> respond(exchange, 200, "text/plain",
                "User-agent: *\nDisallow: /blocked\n"));
        server.createContext("/offers.json", exchange -> {
            String body = feed.get();
            String tag = body.contains("9,99") ? "\"fixture-v1\"" : "\"fixture-v2\"";
            if (tag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.getResponseHeaders().set("ETag", tag);
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
            } else {
                exchange.getResponseHeaders().set("ETag", tag);
                respond(exchange, 200, "application/json", body);
            }
        });
        server.createContext("/blocked.json", exchange -> {
            blockedRequested.set(true);
            respond(exchange, 200, "application/json", feed.get());
        });
        server.createContext("/sitemap.xml", exchange -> respond(exchange, 200, "application/xml",
                "<?xml version=\"1.0\"?><urlset><url><loc>" + baseUrl()
                        + "/structured.html</loc></url><url><loc>" + baseUrl()
                        + "/javascript-only.html</loc></url></urlset>"));
        server.createContext("/structured.html", exchange -> respond(exchange, 200, "text/html",
                """
                        <html><script type="application/ld+json">
                        {"@type":"Product","name":"Structured plan","sku":"structured-1",
                         "url":"https://127.0.0.1/plans/structured","description":"Subscription",
                         "offers":{"@type":"Offer","price":"19.99","priceCurrency":"EUR",
                                   "billingPeriod":"MONTHLY"}}
                        </script></html>
                        """));
        server.createContext("/plans.html", exchange -> respond(exchange, 200, "text/html",
                """
                        <div class="plan"><h2 class="name">HTML plan</h2>
                        <span class="price">4,99 € / mois</span>
                        <a href="https://127.0.0.1/plans/html">details</a></div>
                        """));
        server.createContext("/javascript-only.html", exchange ->
                respond(exchange, 200, "text/html", "<html><body>Plans require scripts</body></html>"));
        server.start();

        assertThat(priceNormalization.normalize("9,99 € pendant 6 mois", null, null).amountMinor())
                .isEqualTo(999L);
        assertThat(priceNormalization.normalize("99,99 € par an", null, null).billingPeriod())
                .isEqualTo(OfferBillingPeriod.YEARLY);
        assertThat(priceNormalization.normalize("À partir de 9,99 € / mois", null, null).startingAtPrice())
                .isTrue();
        assertThat(priceNormalization.normalize("9,99 € puis 12,99 € / mois", null, null).ambiguous())
                .isTrue();
        assertThat(jsonExtractor.extractJsonLd("<script type='application/ld+json'>{bad}</script>",
                com.eparly.api.offer.domain.OfferSector.STREAMING, "https://127.0.0.1/page")).isEmpty();
        assertThat(htmlExtractor.extract("<div class='p'><b>Plan</b><i>8 € / mois</i></div>",
                "https://127.0.0.1", """
                        {"offerSelector":".p","nameSelector":"b","priceSelector":"i",
                         "billingPeriod":"MONTHLY"}
                        """, com.eparly.api.offer.domain.OfferSector.STREAMING)).hasSize(1);
        assertThat(sitemapDiscovery.discover("<urlset><url><loc>https://x.test/a</loc></url></urlset>", 10))
                .containsExactly("https://x.test/a");

        registerSource("JSON_FEED", "/offers.json", "{}");
        registerSource("SITEMAP", "/sitemap.xml", "{}");
        registerSource("HTML_PAGE", "/plans.html", """
                {"offerSelector":".plan","nameSelector":".name","priceSelector":".price",
                 "urlSelector":"a","billingPeriod":"MONTHLY"}
                """);
        registerSource("JSON_FEED", "/blocked.json", "{}");

        var firstRun = jobService.runNow();
        assertThat(firstRun.status()).isEqualTo("SUCCESS");
        assertThat(firstRun.sourcesConfigured()).isEqualTo(4);
        assertThat(firstRun.sourcesProcessed()).isEqualTo(4);
        assertThat(firstRun.pagesFetched()).isEqualTo(5);
        assertThat(firstRun.pagesUnsupported()).isEqualTo(1);
        assertThat(firstRun.offersDiscovered()).isEqualTo(3);
        assertThat(firstRun.errorsCount()).isZero();
        assertThat(blockedRequested).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_sync_sources WHERE robots_status = 'DISALLOWED'
                """, Integer.class)).isEqualTo(1);
        assertThat(count("catalog_offer_observations")).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_offer_observations WHERE validation_status = 'UNSUPPORTED'
                """, Integer.class)).isEqualTo(1);
        assertThat(count("catalog_offer_candidates")).isEqualTo(3);
        assertThat(count("commercial_offers")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_offer_candidates
                WHERE candidate_status = 'NEEDS_REVIEW'
                """, Integer.class)).isEqualTo(3);

        var secondRun = jobService.runNow();
        assertThat(secondRun.status()).isEqualTo("SUCCESS");
        assertThat(secondRun.offersUnchanged()).isEqualTo(2);
        assertThat(count("catalog_offer_observations")).isEqualTo(4);
        assertThat(count("catalog_offer_candidates")).isEqualTo(3);

        feed.set(feed("12,99 € par mois"));
        var thirdRun = jobService.runNow();
        assertThat(thirdRun.offersUpdated()).isEqualTo(1);
        assertThat(count("catalog_offer_observations")).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_offer_candidates
                WHERE change_type = 'PRICE_CHANGED' AND candidate_status = 'NEEDS_REVIEW'
                """, Integer.class)).isEqualTo(1);
        assertThat(count("offer_pricing")).isZero();
    }

    private void registerSource(String type, String path, String config) {
        jdbc.update("""
                INSERT INTO catalog_sync_sources
                    (source_name, official_domain, source_url, source_type, sector, country,
                     collection_enabled, permission_status, permission_reason, permission_updated_at,
                     extractor_config)
                VALUES (?, '127.0.0.1', ?, ?, 'STREAMING', 'FR', TRUE, 'AUTHORIZED',
                        'Local integration source authorized for testing', CURRENT_TIMESTAMP, CAST(? AS jsonb))
                """, "Fixture " + type, baseUrl() + path, type, config);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String feed(String price) {
        return """
                {"offers":[{"id":"fixture-plan","name":"JSON feed plan",
                  "url":"https://127.0.0.1/plans/feed","price":"%s",
                  "currency":"EUR","eligibility":"Available to new customers",
                  "characteristics":{"profiles":2}}]}
                """.formatted(price);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
                                String contentType, String body) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
