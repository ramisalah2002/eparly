package com.eparly.api.catalogsync.fetch;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.validation.SourceTrustPolicy;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultOfficialSourceHttpClient implements OfficialSourceHttpClient {
    private final CatalogSyncProperties properties;
    private final SourceTrustPolicy trustPolicy;
    private final CloseableHttpClient client;
    private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> nextRequestNanos = new ConcurrentHashMap<>();

    public DefaultOfficialSourceHttpClient(CatalogSyncProperties properties, SourceTrustPolicy trustPolicy,
                                           PinnedDnsResolver dnsResolver) {
        this.properties = properties;
        this.trustPolicy = trustPolicy;
        if (properties.getMaxRetries() < 0 || properties.getMaxRetries() > 5) {
            throw new IllegalArgumentException("Catalog sync max retries must be between 0 and 5");
        }
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(timeout(properties.getConnectTimeout()))
                                .build())
                        .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(timeout(properties.getRequestTimeout()))
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
    }

    @Override
    public FetchResult fetch(CatalogSyncSource source, String url, String etag, String lastModified) {
        URI current = trustPolicy.validate(source, url);
        for (int redirect = 0; redirect <= properties.getMaxRedirects(); redirect++) {
            Response response = send(source, current, etag, lastModified);
            int status = response.statusCode();
            if (status >= 300 && status < 400 && status != 304) {
                if (response.location() == null) {
                    throw new IllegalStateException("Redirect response has no Location");
                }
                if (redirect == properties.getMaxRedirects()) {
                    throw new IllegalStateException("Source exceeded the redirect limit");
                }
                current = trustPolicy.validate(source, current.resolve(response.location()).toString());
                continue;
            }
            if (status == 304) {
                return new FetchResult(status, current, null, "", response.etag(), response.lastModified());
            }
            if (status == 404) {
                return new FetchResult(status, current, null, "", response.etag(), response.lastModified());
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Source returned HTTP " + status);
            }
            if (!acceptedContentType(response.contentType())) {
                throw new IllegalStateException("Unsupported source content type");
            }
            return new FetchResult(status, current, response.contentType(), response.body(),
                    response.etag(), response.lastModified());
        }
        throw new IllegalStateException("Source redirect could not be followed");
    }

    private Response send(CatalogSyncSource source, URI uri, String etag, String lastModified) {
        for (int attempt = 0; ; attempt++) {
            try {
                awaitRequestSlot(uri.getHost());
                HttpGet request = new HttpGet(uri);
                request.setHeader("User-Agent", userAgent());
                request.setHeader("Accept",
                        "application/json, application/ld+json, text/html, application/xml, text/xml");
                if (etag != null && uri.toString().equals(source.sourceUrl())) {
                    request.setHeader("If-None-Match", etag);
                }
                if (lastModified != null && uri.toString().equals(source.sourceUrl())) {
                    request.setHeader("If-Modified-Since", lastModified);
                }

                Response received;
                try (CloseableHttpResponse response = client.execute(request)) {
                    int status = response.getCode();
                    String retryAfter = header(response, "Retry-After");
                    boolean retry = (status == 429 || status >= 500) && attempt < properties.getMaxRetries();
                    String body = !retry && status >= 200 && status < 300 && status != 304
                            ? readBounded(response.getEntity() == null
                            ? InputStream.nullInputStream() : response.getEntity().getContent())
                            : "";
                    received = new Response(status, header(response, "Content-Type"),
                            header(response, "ETag"), header(response, "Last-Modified"),
                            header(response, "Location"), retryAfter, body);
                }
                if ((received.statusCode() == 429 || received.statusCode() >= 500)
                        && attempt < properties.getMaxRetries()) {
                    sleep(retryDelay(received.retryAfter(), attempt));
                    continue;
                }
                return received;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching an authorized source", exception);
            } catch (IOException exception) {
                throw new IllegalStateException("Authorized source could not be fetched", exception);
            }
        }
    }

    private void awaitRequestSlot(String host) throws InterruptedException {
        if (properties.getRequestDelay().isZero() || properties.getRequestDelay().isNegative()) return;
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        Object lock = hostLocks.computeIfAbsent(normalizedHost, ignored -> new Object());
        synchronized (lock) {
            long now = System.nanoTime();
            long next = nextRequestNanos.getOrDefault(normalizedHost, now);
            long waitNanos = next - now;
            if (waitNanos > 0) {
                long millis = waitNanos / 1_000_000;
                int nanos = (int) (waitNanos % 1_000_000);
                Thread.sleep(millis, nanos);
            }
            nextRequestNanos.put(normalizedHost, System.nanoTime() + properties.getRequestDelay().toNanos());
        }
    }

    private String userAgent() {
        return properties.getContact().isBlank()
                ? properties.getUserAgent()
                : properties.getUserAgent() + " (" + properties.getContact() + ")";
    }

    private String readBounded(InputStream input) throws IOException {
        try (input) {
            byte[] data = input.readNBytes(properties.getMaxResponseBytes() + 1);
            if (data.length > properties.getMaxResponseBytes()) {
                throw new IllegalStateException("Source response exceeded the configured size limit");
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static Duration retryDelay(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                return Duration.ofSeconds(Math.min(30, Math.max(0, seconds)));
            } catch (NumberFormatException ignored) {
                try {
                    long seconds = Duration.between(java.time.Instant.now(),
                            ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                            .toSeconds();
                    return Duration.ofSeconds(Math.min(30, Math.max(0, seconds)));
                } catch (Exception ignoredDate) {
                    // Invalid Retry-After falls back to bounded exponential backoff.
                }
            }
        }
        return Duration.ofSeconds(1L << attempt);
    }

    private static boolean acceptedContentType(String contentType) {
        if (contentType == null) return false;
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.equals("application/json") || type.equals("application/ld+json")
                || type.equals("text/html") || type.equals("application/xhtml+xml")
                || type.equals("application/xml") || type.equals("text/xml")
                || type.equals("text/plain");
    }

    private static String header(CloseableHttpResponse response, String name) {
        return response.getFirstHeader(name) == null ? null : response.getFirstHeader(name).getValue();
    }

    private static Timeout timeout(Duration duration) {
        return Timeout.ofMilliseconds(Math.max(1, duration.toMillis()));
    }

    private static void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    @PreDestroy
    public void close() {
        try {
            client.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close the catalog sync HTTP client", exception);
        }
    }

    private record Response(int statusCode, String contentType, String etag, String lastModified,
                            String location, String retryAfter, String body) { }
}
