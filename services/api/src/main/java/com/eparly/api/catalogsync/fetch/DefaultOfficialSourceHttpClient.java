package com.eparly.api.catalogsync.fetch;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.validation.SourceTrustPolicy;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultOfficialSourceHttpClient implements OfficialSourceHttpClient {
    private static final int MAX_RETRIES = 2;
    private final CatalogSyncProperties properties;
    private final SourceTrustPolicy trustPolicy;
    private final HttpClient client;
    private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> nextRequestNanos = new ConcurrentHashMap<>();

    public DefaultOfficialSourceHttpClient(CatalogSyncProperties properties, SourceTrustPolicy trustPolicy) {
        this.properties = properties;
        this.trustPolicy = trustPolicy;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new NoProxySelector())
                .build();
    }

    @Override
    public FetchResult fetch(CatalogSyncSource source, String url, String etag, String lastModified) {
        URI current = trustPolicy.validate(source, url);
        for (int redirect = 0; redirect <= properties.getMaxRedirects(); redirect++) {
            HttpResponse<InputStream> response = send(source, current, etag, lastModified);
            int status = response.statusCode();
            if (status >= 300 && status < 400 && status != 304) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalStateException("Redirect response has no Location"));
                close(response.body());
                if (redirect == properties.getMaxRedirects()) {
                    throw new IllegalStateException("Source exceeded the redirect limit");
                }
                current = trustPolicy.validate(source, current.resolve(location).toString());
                continue;
            }
            if (status == 304) {
                close(response.body());
                return new FetchResult(status, current, null, "", response.headers()
                        .firstValue("etag").orElse(etag), response.headers()
                        .firstValue("last-modified").orElse(lastModified));
            }
            if ((status < 200 || status >= 300) && status != 404) {
                close(response.body());
                throw new IllegalStateException("Source returned HTTP " + status);
            }
            if (status == 404) {
                close(response.body());
                return new FetchResult(status, current, null, "",
                        response.headers().firstValue("etag").orElse(null),
                        response.headers().firstValue("last-modified").orElse(null));
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!acceptedContentType(contentType)) {
                close(response.body());
                throw new IllegalStateException("Unsupported source content type");
            }
            String body = readBounded(response.body());
            return new FetchResult(status, current, contentType, body,
                    response.headers().firstValue("etag").orElse(null),
                    response.headers().firstValue("last-modified").orElse(null));
        }
        throw new IllegalStateException("Source redirect could not be followed");
    }

    private HttpResponse<InputStream> send(CatalogSyncSource source, URI uri, String etag, String lastModified) {
        for (int attempt = 0; ; attempt++) {
            try {
                awaitRequestSlot(uri.getHost());
                HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                        .timeout(properties.getRequestTimeout())
                        .header("User-Agent", userAgent())
                        .header("Accept", "application/json, application/ld+json, text/html, application/xml, text/xml");
                if (etag != null && uri.toString().equals(source.sourceUrl())) request.header("If-None-Match", etag);
                if (lastModified != null && uri.toString().equals(source.sourceUrl())) {
                    request.header("If-Modified-Since", lastModified);
                }
                HttpResponse<InputStream> response = client.send(request.GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if ((status == 429 || status >= 500) && attempt < MAX_RETRIES) {
                    Duration delay = retryDelay(response, attempt);
                    close(response.body());
                    sleep(delay);
                    continue;
                }
                return response;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching an authorized source", exception);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Authorized source could not be fetched", exception);
            }
        }
    }

    private void awaitRequestSlot(String host) {
        if (properties.getRequestDelay().isZero() || properties.getRequestDelay().isNegative()) return;
        String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
        Object lock = hostLocks.computeIfAbsent(normalizedHost, ignored -> new Object());
        synchronized (lock) {
            long now = System.nanoTime();
            long next = nextRequestNanos.getOrDefault(normalizedHost, now);
            long waitNanos = next - now;
            if (waitNanos > 0) {
                try {
                    long millis = waitNanos / 1_000_000;
                    int nanos = (int) (waitNanos % 1_000_000);
                    Thread.sleep(millis, nanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while rate limiting source requests", exception);
                }
            }
            nextRequestNanos.put(normalizedHost, System.nanoTime() + properties.getRequestDelay().toNanos());
        }
    }

    private String userAgent() {
        return properties.getContact().isBlank()
                ? properties.getUserAgent()
                : properties.getUserAgent() + " (" + properties.getContact() + ")";
    }

    private String readBounded(InputStream input) {
        try (input) {
            byte[] data = input.readNBytes(properties.getMaxResponseBytes() + 1);
            if (data.length > properties.getMaxResponseBytes()) {
                throw new IllegalStateException("Source response exceeded the configured size limit");
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read the bounded source response", exception);
        }
    }

    private static boolean acceptedContentType(String contentType) {
        String type = contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        return type.equals("application/json") || type.equals("application/ld+json")
                || type.equals("text/html") || type.equals("application/xhtml+xml")
                || type.equals("application/xml") || type.equals("text/xml")
                || type.equals("text/plain");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String retryAfter = response.headers().firstValue("retry-after").orElse(null);
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

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during source backoff", exception);
        }
    }

    private static void close(InputStream body) {
        try {
            body.close();
        } catch (java.io.IOException ignored) {
            // Closing a response body is best effort after status handling.
        }
    }

    private static final class NoProxySelector extends ProxySelector {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException exception) {
        }
    }
}
