package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.extraction.StructuredOfferData;
import com.eparly.api.catalogsync.fetch.FetchResult;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service de recherche de la Products API Tradedoubler, d'apres
 * https://dev.tradedoubler.com/products/publisher/ (limite documentee : 1 000 produits par recherche).
 * Desactive tant qu'un jeton n'est pas fourni par l'environnement ; il n'a jamais ete execute contre
 * l'API reelle. Le jeton n'entre ni dans source_url, ni dans une offre, ni dans un message d'erreur.
 */
@Component
public class TradedoublerProductsConnector implements CatalogSourceConnector {
    public static final String KEY = "tradedoubler-products";
    private static final int SEARCH_RESULT_CAP = 1000;
    private static final int PAGE_SIZE = 100;

    private final OfficialSourceHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CatalogSyncProperties properties;

    public TradedoublerProductsConnector(OfficialSourceHttpClient httpClient, ObjectMapper objectMapper,
                                         CatalogSyncProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Readiness readiness() {
        CatalogSyncProperties.Tradedoubler configuration = properties.getTradedoubler();
        if (!configuration.isEnabled()) return Readiness.notConfigured("Connector is disabled by configuration");
        if (configuration.getToken() == null || configuration.getToken().isBlank()) {
            return Readiness.notConfigured("Products API token is not configured");
        }
        return Readiness.isReady();
    }

    @Override
    public List<String> offerDomains(CatalogSyncSource source) {
        List<String> domains = new ArrayList<>();
        readConfig(source).path("merchantDomains").forEach(domain -> domains.add(domain.asText()));
        return domains;
    }

    @Override
    public CollectedSourceBatch collect(CatalogSyncSource source, CollectionBudget budget) {
        JsonNode config = readConfig(source);
        String feedId = config.path("feedId").asText("");
        if (!feedId.matches("\\d+")) {
            throw new IllegalArgumentException("Tradedoubler source requires a numeric feedId in its configuration");
        }
        List<RawCatalogOffer> offers = new ArrayList<>();
        int pages = 0;
        boolean complete = false;
        int maxPages = Math.min(properties.getMaxPagesPerSource(), SEARCH_RESULT_CAP / PAGE_SIZE);
        for (int page = 0; page < maxPages; page++) {
            if (budget.stopping()) break;
            budget.ensureBeforeDeadline();
            FetchResult result = httpClient.fetch(source, pageUrl(source, feedId, page), null, null);
            pages++;
            JsonNode root = readBody(result.body());
            JsonNode products = root.path("products");
            if (!products.isArray()) {
                throw new IllegalArgumentException("Tradedoubler response has no products array");
            }
            products.forEach(product -> offers.addAll(toOffers(product, source)));
            long totalHits = root.path("productHeader").path("totalHits").asLong(0);
            if (products.isEmpty() || (long) (page + 1) * PAGE_SIZE >= totalHits) {
                complete = totalHits <= SEARCH_RESULT_CAP;
                break;
            }
        }
        return new CollectedSourceBatch(offers, List.of(), pages, false, complete && !offers.isEmpty(), null, null);
    }

    /** La documentation fixe limit = pageSize*page+pageSize pour paginer. */
    private String pageUrl(CatalogSyncSource source, String feedId, int page) {
        String token = URLEncoder.encode(properties.getTradedoubler().getToken(), StandardCharsets.UTF_8);
        int limit = PAGE_SIZE * page + PAGE_SIZE;
        return source.sourceUrl() + ";fid=" + feedId + ";page=" + page + ";pageSize=" + PAGE_SIZE
                + ";limit=" + limit + "?token=" + token;
    }

    private List<RawCatalogOffer> toOffers(JsonNode product, CatalogSyncSource source) {
        List<RawCatalogOffer> offers = new ArrayList<>();
        for (JsonNode offer : product.path("offers")) {
            JsonNode latest = latestPrice(offer);
            BigDecimal value = latest == null ? null : decimal(text(latest.path("price"), "value"));
            Long amountMinor = value == null || value.scale() > 2 ? null : value.movePointRight(2).longValueExact();

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", text(offer, "id"));
            raw.put("feedId", text(offer, "feedId"));
            raw.put("sourceProductId", text(offer, "sourceProductId"));
            raw.put("modified", text(offer, "modified"));
            raw.put("price", value == null ? null : value.toPlainString());

            // Les periodicite, engagement et frais ne figurent pas dans le schema documente : ils restent inconnus.
            StructuredOfferData structured = new StructuredOfferData(text(offer, "programName"), null, amountMinor,
                    null, null, null, null, null, null, null, Map.of(), null, null, raw);
            offers.add(new RawCatalogOffer("TD:" + text(offer, "feedId") + ":" + text(offer, "sourceProductId"),
                    text(offer, "sourceProductUrl"), text(product, "name"), text(product, "description"), null,
                    latest == null ? null : text(latest.path("price"), "currency"), null, null, null,
                    Map.of(), List.of(), source.sector(), structured));
        }
        return offers;
    }

    private static JsonNode latestPrice(JsonNode offer) {
        JsonNode latest = null;
        for (JsonNode entry : offer.path("priceHistory")) {
            if (latest == null || entry.path("date").asLong(0) > latest.path("date").asLong(0)) latest = entry;
        }
        return latest;
    }

    private JsonNode readConfig(CatalogSyncSource source) {
        try {
            return objectMapper.readTree(source.extractorConfig() == null ? "{}" : source.extractorConfig());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tradedoubler source configuration is invalid");
        }
    }

    private JsonNode readBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tradedoubler response is not valid JSON");
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isArray() || value.isObject() ? null : value.asText();
    }
}
