package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.discovery.SitemapDiscoveryService;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.extraction.HtmlCatalogOfferExtractor;
import com.eparly.api.catalogsync.extraction.JsonCatalogOfferExtractor;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.fetch.FetchResult;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.catalogsync.fetch.RobotsPolicyService;
import com.eparly.api.catalogsync.fetch.SourceFetchException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Lecteur des sources sans connecteur dedie : flux JSON, API JSON, sitemap, JSON-LD et HTML cible. */
@Component
public class GenericOfficialSourceConnector implements CatalogSourceConnector {
    private final OfficialSourceHttpClient httpClient;
    private final RobotsPolicyService robotsPolicy;
    private final SitemapDiscoveryService sitemapDiscovery;
    private final JsonCatalogOfferExtractor jsonExtractor;
    private final HtmlCatalogOfferExtractor htmlExtractor;
    private final CatalogSyncProperties properties;

    public GenericOfficialSourceConnector(OfficialSourceHttpClient httpClient, RobotsPolicyService robotsPolicy,
                                          SitemapDiscoveryService sitemapDiscovery,
                                          JsonCatalogOfferExtractor jsonExtractor,
                                          HtmlCatalogOfferExtractor htmlExtractor,
                                          CatalogSyncProperties properties) {
        this.httpClient = httpClient;
        this.robotsPolicy = robotsPolicy;
        this.sitemapDiscovery = sitemapDiscovery;
        this.jsonExtractor = jsonExtractor;
        this.htmlExtractor = htmlExtractor;
        this.properties = properties;
    }

    @Override
    public String key() {
        return "generic-official-source";
    }

    @Override
    public Readiness readiness() {
        return Readiness.isReady();
    }

    @Override
    public CollectedSourceBatch collect(CatalogSyncSource source, CollectionBudget budget) {
        budget.ensureBeforeDeadline();
        FetchResult root = httpClient.fetch(source, source.sourceUrl(), source.etag(), source.lastModified());
        if (root.notModified()) return CollectedSourceBatch.notModified(root.etag(), root.lastModified());
        if (root.statusCode() == 404) throw SourceFetchException.forStatus(404);

        List<String> unsupportedPages = new ArrayList<>();
        List<RawCatalogOffer> offers = new ArrayList<>();
        int pagesFetched = 1;
        boolean complete = true;
        if (source.sourceType() == CatalogSourceType.SITEMAP) {
            List<String> urls = sitemapDiscovery.discover(root.body(), properties.getMaxPagesPerSource());
            complete = urls.size() < properties.getMaxPagesPerSource();
            for (String url : urls) {
                if (budget.stopping()) {
                    complete = false;
                    break;
                }
                budget.ensureBeforeDeadline();
                if (!robotsPolicy.isAllowed(source, url)) {
                    complete = false;
                    continue;
                }
                FetchResult page = httpClient.fetch(source, url, null, null);
                if (page.notModified()) continue;
                pagesFetched++;
                List<RawCatalogOffer> pageOffers = extractPage(source, page.body(), page.finalUri().toString());
                if (pageOffers.isEmpty()) {
                    unsupportedPages.add(page.finalUri().toString());
                } else {
                    offers.addAll(pageOffers);
                }
            }
        } else {
            offers.addAll(extract(source, root.body(), root.finalUri().toString()));
            if (offers.isEmpty() && (source.sourceType() == CatalogSourceType.JSON_LD
                    || source.sourceType() == CatalogSourceType.HTML_PAGE)) {
                unsupportedPages.add(root.finalUri().toString());
            }
        }
        return new CollectedSourceBatch(offers, unsupportedPages, pagesFetched, false,
                complete && !offers.isEmpty(), root.etag(), root.lastModified());
    }

    private List<RawCatalogOffer> extract(CatalogSyncSource source, String body, String finalUrl) {
        return switch (source.sourceType()) {
            case JSON_FEED, OFFICIAL_API -> jsonExtractor.extractFeed(body, source.sector(), finalUrl);
            case CSV_FEED -> throw new UnsupportedOperationException(
                    "CSV_FEED automatic collection is disabled for V1; manual catalog import is used instead");
            case JSON_LD -> extractPage(source, body, finalUrl);
            case HTML_PAGE -> htmlExtractor.extract(body, finalUrl, source.extractorConfig(), source.sector());
            case SITEMAP -> List.of();
        };
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
}
