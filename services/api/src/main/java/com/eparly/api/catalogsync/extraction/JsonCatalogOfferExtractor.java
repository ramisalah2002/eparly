package com.eparly.api.catalogsync.extraction;

import com.eparly.api.offer.domain.OfferSector;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class JsonCatalogOfferExtractor {
    private final ObjectMapper objectMapper;

    public JsonCatalogOfferExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawCatalogOffer> extractFeed(String json, OfferSector sector, String baseUrl) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.isArray() ? root : root.path("offers");
            if (!items.isArray()) throw new IllegalArgumentException("Feed must be an array or contain an offers array");
            List<RawCatalogOffer> offers = new ArrayList<>();
            for (JsonNode item : items) offers.add(fromFeedItem(item, sector, baseUrl));
            return offers;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON catalog feed", exception);
        }
    }

    public List<RawCatalogOffer> extractJsonLd(String html, OfferSector sector, String baseUrl) {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(html, baseUrl);
        List<RawCatalogOffer> offers = new ArrayList<>();
        for (org.jsoup.nodes.Element script : document.select("script[type=application/ld+json]")) {
            try {
                collectJsonLd(objectMapper.readTree(script.data()), sector, baseUrl, offers);
            } catch (Exception ignored) {
                // A malformed structured-data block does not invalidate other blocks on the page.
            }
        }
        return offers;
    }

    private void collectJsonLd(JsonNode node, OfferSector sector, String baseUrl, List<RawCatalogOffer> offers) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectJsonLd(child, sector, baseUrl, offers));
            return;
        }
        if (!node.isObject()) return;
        String type = node.path("@type").asText("");
        if (type.equalsIgnoreCase("Product") || type.equalsIgnoreCase("Service")) {
            JsonNode offer = node.path("offers");
            if (offer.isArray()) offer = offer.isEmpty() ? objectMapper.createObjectNode() : offer.get(0);
            if (offer.isObject()) {
                String url = text(node, "url");
                if (url == null) url = text(offer, "url");
                String price = text(offer, "price");
                offers.add(new RawCatalogOffer(
                        first(text(node, "sku"), url, text(node, "name")),
                        resolve(baseUrl, url),
                        text(node, "name"),
                        text(node, "description"),
                        price,
                        text(offer, "priceCurrency"),
                        first(text(offer, "billingPeriod"), text(offer, "unitText")),
                        text(offer, "promotion"),
                        text(offer, "eligibleRegion"),
                        Map.of(),
                        List.of(),
                        sector));
            }
        }
        node.forEach(child -> collectJsonLd(child, sector, baseUrl, offers));
    }

    private RawCatalogOffer fromFeedItem(JsonNode item, OfferSector sector, String baseUrl) {
        String url = first(text(item, "canonicalUrl"), text(item, "url"));
        JsonNode priceNode = item.path("price");
        String price = priceNode.isNumber() ? priceNode.asText() : priceNode.isTextual() ? priceNode.asText() : null;
        Map<String, Object> characteristics = new HashMap<>();
        JsonNode attributes = item.path("characteristics");
        if (attributes.isObject()) attributes.properties()
                .forEach(entry -> characteristics.put(entry.getKey(), scalar(entry.getValue())));
        List<String> conditions = new ArrayList<>();
        JsonNode conditionNode = item.path("conditions");
        if (conditionNode.isArray()) conditionNode.forEach(value -> conditions.add(value.asText()));
        return new RawCatalogOffer(
                first(text(item, "id"), url, text(item, "name")),
                resolve(baseUrl, url),
                text(item, "name"),
                text(item, "description"),
                price,
                first(text(item, "currency"), text(item, "priceCurrency")),
                first(text(item, "billingPeriod"), text(item, "unitText")),
                text(item, "promotionText"),
                text(item, "eligibility"),
                characteristics,
                conditions,
                sector);
    }

    private static Object scalar(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.numberValue();
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> values.add(value.asText()));
            return values;
        }
        return node.asText();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isArray() || value.isObject() ? null : value.asText();
    }

    private static String resolve(String baseUrl, String value) {
        if (value == null) return null;
        try {
            return java.net.URI.create(baseUrl).resolve(value).toString();
        } catch (Exception exception) {
            return value;
        }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
