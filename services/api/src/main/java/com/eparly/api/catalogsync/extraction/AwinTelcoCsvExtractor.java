package com.eparly.api.catalogsync.extraction;

import com.eparly.api.offer.domain.OfferSector;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AwinTelcoCsvExtractor {
    private static final String[] JSON_FIELDS = {
            "network_details_json", "tariff_group_details_json", "tariff_details_json",
            "tariff_allowances_json", "deal_cost_json", "deal_discounts_json",
            "deal_type_json", "deal_extras_json", "deal_cashback_json"
    };

    private final ObjectMapper objectMapper;

    public AwinTelcoCsvExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawCatalogOffer> extract(String csv, OfferSector sector) {
        if (sector != OfferSector.MOBILE_PLAN) {
            throw new IllegalArgumentException("Awin enhanced telco feeds are only supported for mobile plans");
        }
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(new StringReader(csv))) {
            Map<String, Integer> headers = normalizedHeaders(parser.getHeaderMap());
            requireHeaders(headers, "merchant_product_id", "merchant_deep_link", "product_name");
            List<RawCatalogOffer> offers = new ArrayList<>();
            for (CSVRecord row : parser) {
                String externalId = value(row, headers, "merchant_product_id");
                String productName = value(row, headers, "product_name");
                String url = value(row, headers, "merchant_deep_link");
                if (externalId == null || productName == null || url == null) {
                    throw new IllegalArgumentException("Awin telco row is missing its product ID, name or deep link");
                }

                Map<String, Object> characteristics = new LinkedHashMap<>();
                JsonNode network = jsonField(row, headers, "network_details_json");
                JsonNode tariff = jsonField(row, headers, "tariff_details_json");
                JsonNode allowances = jsonField(row, headers, "tariff_allowances_json");
                put(characteristics, "networkName", text(network, "name"));
                put(characteristics, "tariffName", text(tariff, "tariff_name"));
                put(characteristics, "commitmentMonths", text(tariff, "tariff_term"));
                JsonNode data = allowances.path("data_details");
                put(characteristics, "mobileDataMb", text(data, "cellular_mb"));
                put(characteristics, "mobileDataSpeed", text(data, "cellular_speed"));
                put(characteristics, "sourceLastUpdated", value(row, headers, "last_updated"));
                put(characteristics, "sourceMerchant", value(row, headers, "merchant_name"));

                JsonNode costs = jsonField(row, headers, "deal_cost_json");
                String monthlyPrice = text(costs, "monthly_total_inc_vat");
                String currency = value(row, headers, "currency");
                String priceText = monthlyPrice == null ? null
                        : currency == null ? monthlyPrice : monthlyPrice + " " + currency;
                String promoText = promotionText(row, headers);
                List<String> conditions = new ArrayList<>();
                add(conditions, value(row, headers, "terms_of_contract"));
                add(conditions, value(row, headers, "product_type"));
                add(conditions, promoText);

                offers.add(new RawCatalogOffer(externalId, url, productName,
                        value(row, headers, "description"), priceText, currency,
                        monthlyPrice == null ? null : "MONTHLY", promoText, null,
                        Map.copyOf(characteristics), List.copyOf(conditions), sector));
            }
            return List.copyOf(offers);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Malformed Awin telco CSV", exception);
        }
    }

    private String promotionText(CSVRecord row, Map<String, Integer> headers) {
        List<String> details = new ArrayList<>();
        add(details, value(row, headers, "promotional_text"));
        add(details, text(jsonField(row, headers, "tariff_group_details_json"), "tariff_group_promo_info"));
        add(details, text(jsonField(row, headers, "tariff_details_json"), "tariff_promo_info"));
        add(details, value(row, headers, "deal_promo_info"));
        JsonNode discounts = jsonField(row, headers, "deal_discounts_json");
        collectDiscountDescriptions(discounts, details);
        return details.isEmpty() ? null : String.join(" | ", details);
    }

    private static void collectDiscountDescriptions(JsonNode node, List<String> descriptions) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectDiscountDescriptions(child, descriptions));
            return;
        }
        if (!node.isObject()) return;
        add(descriptions, text(node, "nd_description"));
        add(descriptions, text(node, "rd_description"));
        node.forEach(child -> {
            if (child.isObject() || child.isArray()) collectDiscountDescriptions(child, descriptions);
        });
    }

    private JsonNode jsonField(CSVRecord row, Map<String, Integer> headers, String field) {
        String value = value(row, headers, field);
        if (value == null) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed JSON in Awin telco field " + field, exception);
        }
    }

    private static Map<String, Integer> normalizedHeaders(Map<String, Integer> input) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        input.forEach((name, index) -> normalized.put(name.trim().toLowerCase(Locale.ROOT), index));
        return normalized;
    }

    private static void requireHeaders(Map<String, Integer> headers, String... required) {
        for (String field : required) {
            if (!headers.containsKey(field)) {
                throw new IllegalArgumentException("Awin telco CSV is missing required column " + field);
            }
        }
    }

    private static String value(CSVRecord row, Map<String, Integer> headers, String field) {
        Integer index = headers.get(field);
        if (index == null || index >= row.size()) return null;
        String result = row.get(index).trim();
        return result.isEmpty() ? null : result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isArray() || value.isObject() ? null : value.asText();
    }

    private static void put(Map<String, Object> values, String name, String value) {
        if (value != null && !value.isBlank()) values.put(name, value);
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) values.add(value);
    }
}
