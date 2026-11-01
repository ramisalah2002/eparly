package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.discovery.SitemapDiscoveryService;
import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.extraction.HtmlCatalogOfferExtractor;
import com.eparly.api.catalogsync.extraction.JsonCatalogOfferExtractor;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.fetch.FetchResult;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.catalogsync.fetch.RobotsPolicyService;
import com.eparly.api.catalogsync.normalization.NormalizedCatalogOffer;
import com.eparly.api.catalogsync.normalization.OfferNormalizationService;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CatalogSyncCoordinator {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncCoordinator.class);

    private final CatalogSyncRepository repository;
    private final OfficialSourceHttpClient httpClient;
    private final RobotsPolicyService robotsPolicy;
    private final SitemapDiscoveryService sitemapDiscovery;
    private final JsonCatalogOfferExtractor jsonExtractor;
    private final HtmlCatalogOfferExtractor htmlExtractor;
    private final OfferNormalizationService normalization;
    private final CatalogSyncProperties properties;
    private final Clock clock;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public CatalogSyncCoordinator(CatalogSyncRepository repository, OfficialSourceHttpClient httpClient,
                                  RobotsPolicyService robotsPolicy, SitemapDiscoveryService sitemapDiscovery,
                                  JsonCatalogOfferExtractor jsonExtractor,
                                  HtmlCatalogOfferExtractor htmlExtractor,
                                  OfferNormalizationService normalization,
                                  CatalogSyncProperties properties, Clock clock) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.robotsPolicy = robotsPolicy;
        this.sitemapDiscovery = sitemapDiscovery;
        this.jsonExtractor = jsonExtractor;
        this.htmlExtractor = htmlExtractor;
        this.normalization = normalization;
        this.properties = properties;
        this.clock = clock;
        if (properties.getMaxExecutionDuration() == null
                || properties.getMaxExecutionDuration().isZero()
                || properties.getMaxExecutionDuration().isNegative()) {
            throw new IllegalArgumentException("Catalog sync maximum execution duration must be positive");
        }
    }

    public CatalogSyncRepository.SyncCounters collect(List<CatalogSyncSource> sources,
                                                       Map<String, String> sourceErrors) {
        robotsPolicy.beginRun();
        CatalogSyncRepository.SyncCounters counters = new CatalogSyncRepository.SyncCounters();
        long deadlineNanos = System.nanoTime() + properties.getMaxExecutionDuration().toNanos();
        for (CatalogSyncSource source : sources) {
            if (stopping.get()) break;
            if (deadlineReached(deadlineNanos)) {
                counters.errorsCount++;
                sourceErrors.put("run", "Maximum catalog sync execution duration reached");
                break;
            }
            counters.sourcesProcessed++;
            try {
                FetchResult root = processSource(source, counters, deadlineNanos);
                if (root != null) {
                    repository.markSourceSuccess(source, root.etag(), root.lastModified(), clock.instant());
                }
            } catch (RuntimeException exception) {
                counters.errorsCount++;
                String safeMessage = safeError(exception);
                sourceErrors.put(source.id().toString(), safeMessage);
                repository.markSourceError(source.id(), safeMessage, clock.instant());
                log.warn("Catalog sync source {} failed: {}", source.id(), safeMessage);
                if (exception instanceof RunDeadlineExceededException) break;
            }
        }
        return counters;
    }

    @PreDestroy
    public void stopAcceptingWork() {
        stopping.set(true);
    }

    private FetchResult processSource(CatalogSyncSource source, CatalogSyncRepository.SyncCounters counters,
                                      long deadlineNanos) {
        ensureBeforeDeadline(deadlineNanos);
        boolean robotsAllowed;
        try {
            robotsAllowed = robotsPolicy.isAllowed(source, source.sourceUrl());
        } catch (RuntimeException exception) {
            repository.updateSourceRobotsStatus(source.id(), "ERROR", clock.instant());
            throw exception;
        }
        if (!robotsAllowed) {
            repository.updateSourceRobotsStatus(source.id(), "DISALLOWED", clock.instant());
            return null;
        }
        repository.updateSourceRobotsStatus(source.id(), "ALLOWED", clock.instant());
        ensureBeforeDeadline(deadlineNanos);
        FetchResult root = httpClient.fetch(source, source.sourceUrl(), source.etag(), source.lastModified());
        if (root.notModified()) {
            return root;
        }
        counters.pagesFetched++;
        List<RawCatalogOffer> offers;
        if (source.sourceType() == CatalogSourceType.SITEMAP) {
            offers = collectSitemapPages(source, root.body(), counters, deadlineNanos);
        } else {
            offers = extract(source, root.body(), root.finalUri().toString());
            if (offers.isEmpty() && (source.sourceType() == CatalogSourceType.JSON_LD
                    || source.sourceType() == CatalogSourceType.HTML_PAGE)) {
                persistUnsupportedPage(source, root.finalUri().toString(), counters);
            }
        }
        for (RawCatalogOffer raw : offers) {
            if (stopping.get()) break;
            ensureBeforeDeadline(deadlineNanos);
            NormalizedCatalogOffer normalized = normalization.normalize(raw, source.officialDomain());
            CatalogSyncRepository.ObservationResult saved =
                    repository.saveObservation(source, normalized, clock.instant());
            if (!saved.newObservation()) {
                counters.offersUnchanged++;
            } else if (saved.rejected()) {
                counters.offersRejected++;
            } else if (saved.knownBefore()) {
                counters.offersUpdated++;
            } else {
                counters.offersDiscovered++;
            }
        }
        return root;
    }

    private List<RawCatalogOffer> collectSitemapPages(CatalogSyncSource source, String sitemap,
                                                      CatalogSyncRepository.SyncCounters counters,
                                                      long deadlineNanos) {
        List<String> urls = sitemapDiscovery.discover(sitemap, properties.getMaxPagesPerSource());
        List<RawCatalogOffer> offers = new ArrayList<>();
        for (String url : urls) {
            if (stopping.get()) break;
            ensureBeforeDeadline(deadlineNanos);
            if (!robotsPolicy.isAllowed(source, url)) continue;
            FetchResult page = httpClient.fetch(source, url, null, null);
            if (page.notModified()) continue;
            counters.pagesFetched++;
            List<RawCatalogOffer> pageOffers = extractPage(source, page.body(), page.finalUri().toString());
            if (pageOffers.isEmpty()) {
                persistUnsupportedPage(source, page.finalUri().toString(), counters);
            } else {
                offers.addAll(pageOffers);
            }
        }
        return offers;
    }

    private static void ensureBeforeDeadline(long deadlineNanos) {
        if (deadlineReached(deadlineNanos)) throw new RunDeadlineExceededException();
    }

    private static boolean deadlineReached(long deadlineNanos) {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    private static final class RunDeadlineExceededException extends RuntimeException {
        private RunDeadlineExceededException() {
            super("Maximum catalog sync execution duration reached");
        }
    }

    private List<RawCatalogOffer> extract(CatalogSyncSource source, String body, String finalUrl) {
        return switch (source.sourceType()) {
            case JSON_FEED, OFFICIAL_API -> jsonExtractor.extractFeed(body, source.sector(), finalUrl);
            case CSV_FEED -> extractCsvFeed(source, body);
            case JSON_LD -> extractPage(source, body, finalUrl);
            case HTML_PAGE -> htmlExtractor.extract(body, finalUrl, source.extractorConfig(), source.sector());
            case SITEMAP -> List.of();
        };
    }

    private List<RawCatalogOffer> extractCsvFeed(CatalogSyncSource source, String body) {
        throw new UnsupportedOperationException(
                "CSV_FEED automatic collection is disabled for V1; manual catalog import is used instead");
    }

    private List<RawCatalogOffer> extractPage(CatalogSyncSource source, String body, String finalUrl) {
        List<RawCatalogOffer> offers = new ArrayList<>(
                jsonExtractor.extractJsonLd(body, source.sector(), finalUrl));
        if (offers.isEmpty() && source.extractorConfig() != null
                && !source.extractorConfig().isBlank()
                && !source.extractorConfig().equals("{}")) {
            offers.addAll(htmlExtractor.extract(body, finalUrl, source.extractorConfig(), source.sector()));
        }
        return offers;
    }

    private void persistUnsupportedPage(CatalogSyncSource source, String url,
                                        CatalogSyncRepository.SyncCounters counters) {
        NormalizedCatalogOffer observation = normalization.unsupportedPage(url, source.officialDomain(),
                source.sector());
        repository.saveObservation(source, observation, clock.instant());
        counters.pagesUnsupported++;
    }

    private static String safeError(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        message = message.replaceAll("https?://\\S+", "[source-url]");
        String result = exception.getClass().getSimpleName()
                + (message.isBlank() ? "" : ": " + message);
        return result.length() > 900 ? result.substring(0, 900) : result;
    }
}
