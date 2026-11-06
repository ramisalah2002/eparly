package com.eparly.api.catalogsync.fetch;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.validation.SourceTrustPolicy;
import com.eparly.api.offer.domain.OfferSector;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogHttpResilienceTest {
    private HttpServer server;
    private DefaultOfficialSourceHttpClient client;
    private CatalogSyncProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        properties = new CatalogSyncProperties();
        properties.setAllowLoopbackForTests(true);
        properties.setRequestDelay(Duration.ZERO);
        properties.setRequestTimeout(Duration.ofMillis(300));
        properties.setMaxRetries(1);
        properties.setMaxResponseBytes(64);
        client = newClient();
    }

    private DefaultOfficialSourceHttpClient newClient() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        SourceTrustPolicy trustPolicy = new SourceTrustPolicy(properties, environment);
        return new DefaultOfficialSourceHttpClient(properties, trustPolicy, new PinnedDnsResolver(trustPolicy));
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop(0);
    }

    @Test
    void timeoutIsRetriedThenReportedAsTimeout() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/slow", exchange -> {
            attempts.incrementAndGet();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThatThrownBy(() -> client.fetch(source("/slow"), url("/slow"), null, null))
                .isInstanceOfSatisfying(SourceFetchException.class,
                        exception -> assertThat(exception.code()).isEqualTo(SourceFetchException.Code.TIMEOUT));
        assertThat(attempts).hasValue(2);
    }

    @Test
    void persistentRateLimitIsRetriedOnceThenClassified() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/limited", exchange -> {
            attempts.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client.fetch(source("/limited"), url("/limited"), null, null))
                .isInstanceOfSatisfying(SourceFetchException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(SourceFetchException.Code.HTTP_RATE_LIMITED);
                    assertThat(exception.getMessage()).isEqualTo("Source returned HTTP 429");
                });
        assertThat(attempts).hasValue(2);
    }

    @Test
    void serverErrorRecoversOnRetryAndCsvIsAccepted() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/flaky.csv", exchange -> {
            if (attempts.getAndIncrement() == 0) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            } else {
                respond(exchange, "text/csv", "A;B\n1;2\n");
            }
        });

        FetchResult result = client.fetch(source("/flaky.csv"), url("/flaky.csv"), null, null);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("A;B");
    }

    @Test
    void oversizedResponseAndUnexpectedContentTypeAreRefused() {
        server.createContext("/big", exchange -> respond(exchange, "application/json", "x".repeat(65)));
        server.createContext("/binary", exchange -> respond(exchange, "application/octet-stream", "x"));

        assertThatThrownBy(() -> client.fetch(source("/big"), url("/big"), null, null))
                .hasMessageContaining("size limit");
        assertThatThrownBy(() -> client.fetch(source("/binary"), url("/binary"), null, null))
                .hasMessageContaining("content type");
    }

    @Test
    void robotsWildcardsAreHonoredAgainstPathAndQuery() {
        assertThat(RobotsPolicyService.matches("/*?id=*", "/page?id=4")).isTrue();
        assertThat(RobotsPolicyService.matches("/*?id=*", "/fileadmin/Option_Base.csv")).isFalse();
        assertThat(RobotsPolicyService.matches("/recherche", "/recherche/x")).isTrue();
        assertThat(RobotsPolicyService.matches("/*.csv$", "/a/b.csv")).isTrue();
        assertThat(RobotsPolicyService.matches("/*.csv$", "/a/b.csv?x=1")).isFalse();
        assertThat(RobotsPolicyService.matches("/", "/anything")).isTrue();
    }

    private CatalogSyncSource source(String path) {
        return new CatalogSyncSource(UUID.randomUUID(), null, "Local fixture", "127.0.0.1", url(path),
                CatalogSourceType.JSON_FEED, OfferSector.GAMING, true, "AUTHORIZED", null, null, "{}");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
