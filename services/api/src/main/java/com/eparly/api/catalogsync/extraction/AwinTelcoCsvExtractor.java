package com.eparly.api.catalogsync.extraction;

import com.eparly.api.offer.domain.OfferSector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AwinTelcoCsvExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AwinTelcoCsvExtractor.class);
    private final ObjectMapper objectMapper;

    public AwinTelcoCsvExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts offers from Awin Enhanced Telco CSV format.
     * Only supports MOBILE_PLAN sector.
     *
     * @param csv the CSV content
     * @param sector the offer sector (must be MOBILE_PLAN)
     * @return list of extracted RawCatalogOffer
     * @throws IllegalArgumentException if CSV is malformed or sector is not MOBILE_PLAN
     */
    public List<RawCatalogOffer> extract(String csv, OfferSector sector) {
        if (sector != OfferSector.MOBILE_PLAN) {
            throw new IllegalArgumentException("AwinTelcoCsvExtractor is only supported for mobile plans");
        }
        
        List<RawCatalogOffer> offers = new ArrayList<>();
        try (CSVParser csvParser = new CSVParser(new StringReader(csv), 
                CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            Map<String, Integer> headers = new LinkedHashMap<>();
            for (String name : csvParser.getHeaderNames()) {
                headers.put(name.toLowerCase(), csvParser.getHeaderMap().get(name));
            }

            // Verify required header exists
            if (!headers.containsKey("merchant_product_id") && !headers.containsKey("deal_id")) {
                throw new IllegalArgumentException("CSV missing required product identifier column (merchant_product_id or deal_id)");
            }

            for (CSVRecord row : csvParser) {
                RawCatalogOffer offer = extractRecord(row, headers);
                if (offer != null) offers.add(offer);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid CSV format", e);
        }
        return offers;
    }

    private RawCatalogOffer extractRecord(CSVRecord row, Map<String, Integer> headers) {
        String externalKey = value(row, headers, "merchant_product_id");
        if (externalKey == null) {
            externalKey = value(row, headers, "deal_id");
        }
        if (externalKey == null || externalKey.isBlank()) {
            throw new IllegalArgumentException("Row is missing its product ID");
        }

        String merchant = value(row, headers, "merchant");
        String name = value(row, headers, "product_name");
        if (name == null) name = value(row, headers, "offer_name");
        if (name == null) name = value(row, headers, "deal_name");
        if (name == null) name = "Unnamed " + externalKey;

        String canonicalUrl = value(row, headers, "merchant_deep_link");
        if (canonicalUrl == null) canonicalUrl = value(row, headers, "deep_link");

        String description = data(row, headers);
        String currency = value(row, headers, "currency");
        if (currency == null) currency = "EUR";

        // Extract structured fields
        String productType = value(row, headers, "product_type");
        String tariffType = value(row, headers, "tariff_type");
        
        JsonNode deviceProduct = jsonField(row, headers, "device_product_json");
        JsonNode costs = jsonField(row, headers, "costs_json");
        JsonNode dealCosts = jsonField(row, headers, "deal_cost_json");
        JsonNode discounts = jsonField(row, headers, "deal_discounts_json");
        
        // Use costs_json if available, otherwise fall back to deal_cost_json
        if (costs == null || costs.isNull()) {
            costs = dealCosts;
        }
        
        // Extract pricing fields
        String monthlyTotalIncVat = valueOrFromJson(costs, "monthly_total_inc_vat", row, headers);
        String monthlyServiceIncVat = valueOrFromJson(costs, "monthly_service_inc_vat", row, headers);
        String monthlyDeviceIncVat = valueOrFromJson(costs, "monthly_device_inc_vat", row, headers);
        String upfrontIncVat = valueOrFromJson(costs, "upfront_inc_vat", row, headers);
        String oneoffIncVat = valueOrFromJson(costs, "oneoff_inc_vat", row, headers);
        String currencyFromCosts = valueOrFromJson(costs, "currency", row, headers);
        
        // Determine offer type and SIM-only status
        String offerType = determineOfferType(productType, tariffType, deviceProduct, costs);
        boolean isSimOnly = isSimOnlyOffer(productType, tariffType, deviceProduct, monthlyDeviceIncVat, upfrontIncVat);
        boolean hasPhoneFinancing = hasDeviceFinancing(costs);
        
        // Build price text - simple format: just the price and currency
        // For monthly prices, just use the monthly total + currency (e.g., "12.99 EUR")
        String priceText = null;
        if (monthlyTotalIncVat != null) {
            priceText = currency != null ? monthlyTotalIncVat + " " + currency : monthlyTotalIncVat;
        } else if (monthlyServiceIncVat != null) {
            priceText = currency != null ? monthlyServiceIncVat + " " + currency : monthlyServiceIncVat;
        }
        
        // Build billing period
        String billingPeriod = monthlyTotalIncVat != null || monthlyServiceIncVat != null ? "MONTHLY" : null;
        
        // Build promotion text
        String promotionText = promotionText(row, headers, discounts);
        
        // Determine commitment period from various sources
        String commitmentMonths = null;
        if (costs != null) {
            commitmentMonths = text(costs, "monthly_contract_term_months");
            if (commitmentMonths == null) {
                commitmentMonths = text(costs, "contract_length_months");
            }
        }
        if (commitmentMonths == null && deviceProduct != null) {
            commitmentMonths = text(deviceProduct, "contract_length");
        }
        
        // Eligibility is not extracted from the CSV - leave as null
        // The normalizer will add ELIGIBILITY_NOT_STATED if null
        
        // Build characteristics
        Map<String, Object> characteristics = new LinkedHashMap<>();
        
        // Add pricing characteristics
        if (monthlyTotalIncVat != null) {
            Long standardPriceCents = parsePriceToMinorUnits(monthlyTotalIncVat);
            if (standardPriceCents != null) {
                characteristics.put("standardPriceMinor", standardPriceCents);
            }
        }
        
        // Add commitment - keep as string for consistency with original behavior
        if (commitmentMonths != null) {
            characteristics.put("commitmentMonths", commitmentMonths);
            try {
                int months = Integer.parseInt(commitmentMonths);
                if (months == 12) {
                    characteristics.put("commitment", "12 months");
                } else if (months == 24) {
                    characteristics.put("commitment", "24 months");
                } else {
                    characteristics.put("commitment", commitmentMonths + " months");
                }
            } catch (NumberFormatException e) {
                characteristics.put("commitment", commitmentMonths);
            }
        }
        
        // Add data allowances from tariff_allowances_json or costs
        JsonNode allowances = jsonField(row, headers, "tariff_allowances_json");
        if (allowances != null) {
            extractDataAllowances(allowances, characteristics);
        }
        if (costs != null) {
            extractDataAllowances(costs, characteristics);
        }
        
        // Add device information
        if (deviceProduct != null) {
            characteristics.put("device_brand", text(deviceProduct, "brand"));
            characteristics.put("device_model", text(deviceProduct, "name"));
            characteristics.put("device_manufacturer", text(deviceProduct, "manufacturer"));
            characteristics.put("device_os", text(deviceProduct, "operating_system"));
            characteristics.put("device_colour", text(deviceProduct, "colour"));
            characteristics.put("device_storage", text(deviceProduct, "storage"));
        }
        
        // Add offer type and flags
        characteristics.put("offer_type", offerType);
        characteristics.put("is_sim_only", isSimOnly);
        characteristics.put("has_phone_financing", hasPhoneFinancing);
        characteristics.put("comparable", !hasPhoneFinancing);
        
        // Add raw fields for transparency
        characteristics.put("raw_product_type", productType);
        characteristics.put("raw_tariff_type", tariffType);
        
        // Add source last updated
        String lastUpdated = value(row, headers, "last_updated");
        if (lastUpdated != null) {
            characteristics.put("sourceLastUpdated", lastUpdated);
        }
        
        // Build conditions list - include the raw fields as per original implementation
        List<String> conditions = new ArrayList<>();
        add(conditions, value(row, headers, "terms_of_contract"));
        add(conditions, productType);
        add(conditions, promotionText);
        
        // Parse structured discounts for additional info
        if (discounts != null && discounts.isArray() && !discounts.isEmpty()) {
            List<Map<String, String>> structuredDiscounts = parseStructuredDiscounts(discounts);
            if (!structuredDiscounts.isEmpty()) {
                characteristics.put("structured_discounts", structuredDiscounts);
            }
        }
        
        // Flag for human review if we can't accurately represent the pricing
        if (hasPhoneFinancing) {
            conditions.add("PHONE_FINANCING_REQUIRES_REVIEW");
        }
        
        return new RawCatalogOffer(
                externalKey,
                canonicalUrl,
                name,
                description,
                priceText,
                currency,
                billingPeriod,
                promotionText,
                null, // eligibility - null so normalizer adds ELIGIBILITY_NOT_STATED
                characteristics,
                conditions,
                OfferSector.MOBILE_PLAN
        );
    }

    private void extractDataAllowances(JsonNode node, Map<String, Object> characteristics) {
        if (node == null || node.isNull()) return;
        
        // Try cellular_mb from data_details
        JsonNode dataDetails = node.path("data_details");
        if (dataDetails.isObject()) {
            String mobileDataMb = text(dataDetails, "cellular_mb");
            if (mobileDataMb != null) {
                // Keep as string for consistency with original behavior
                characteristics.put("mobileDataMb", mobileDataMb);
                try {
                    long mb = Long.parseLong(mobileDataMb);
                    characteristics.put("mobileDataGb", mb / 1024.0);
                } catch (NumberFormatException e) {
                    // Keep mobileDataMb as string, don't add mobileDataGb
                }
            }
            characteristics.put("mobileDataSpeed", text(dataDetails, "cellular_speed"));
        }
        
        // Direct fields
        String mobileDataMb = text(node, "cellular_mb");
        if (mobileDataMb != null) {
            // Keep as string for consistency
            characteristics.put("mobileDataMb", mobileDataMb);
            try {
                long mb = Long.parseLong(mobileDataMb);
                characteristics.put("mobileDataGb", mb / 1024.0);
            } catch (NumberFormatException e) {
                // Keep mobileDataMb as string
            }
        }
        
        String mobileData = text(node, "data");
        if (mobileData != null) {
            characteristics.put("mobileData", mobileData);
        }
        
        String dataUnit = text(node, "data_unit");
        if (dataUnit != null) {
            characteristics.put("dataUnit", dataUnit);
        }
    }

    private String valueOrFromJson(JsonNode jsonNode, String field, CSVRecord row, Map<String, Integer> headers) {
        if (jsonNode != null) {
            String value = text(jsonNode, field);
            if (value != null) return value;
        }
        return value(row, headers, field);
    }

    private String value(CSVRecord row, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name.toLowerCase());
        return index != null && index < row.size() ? row.get(index) : null;
    }

    private JsonNode jsonField(CSVRecord row, Map<String, Integer> headers, String name) {
        String text = value(row, headers, name);
        if (text == null || text.isBlank()) return null;
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON in field " + name + ": " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isArray() || value.isObject() ? null : value.asText();
    }

    private String data(CSVRecord row, Map<String, Integer> headers) {
        List<String> parts = new ArrayList<>();
        add(parts, value(row, headers, "product_name"));
        add(parts, value(row, headers, "offer_name"));
        add(parts, value(row, headers, "deal_name"));
        add(parts, value(row, headers, "description"));
        add(parts, value(row, headers, "device_description"));
        JsonNode deviceProduct = jsonField(row, headers, "device_product_json");
        if (deviceProduct != null) {
            add(parts, text(deviceProduct, "description"));
        }
        JsonNode tariffDetails = jsonField(row, headers, "tariff_details_json");
        if (tariffDetails != null) {
            add(parts, text(tariffDetails, "tariff_description"));
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String promotionText(CSVRecord row, Map<String, Integer> headers, JsonNode discounts) {
        List<String> details = new ArrayList<>();
        add(details, value(row, headers, "promotional_text"));
        JsonNode tariffGroupDetails = jsonField(row, headers, "tariff_group_details_json");
        if (tariffGroupDetails != null) {
            add(details, text(tariffGroupDetails, "tariff_group_promo_info"));
        }
        JsonNode tariffDetails = jsonField(row, headers, "tariff_details_json");
        if (tariffDetails != null) {
            add(details, text(tariffDetails, "tariff_promo_info"));
        }
        add(details, value(row, headers, "deal_promo_info"));
        if (discounts != null) {
            collectDiscountDescriptions(discounts, details);
        }
        return details.isEmpty() ? null : String.join(" | ", details);
    }

    private void collectDiscountDescriptions(JsonNode discounts, List<String> details) {
        if (discounts == null || discounts.isNull() || !discounts.isArray()) return;
        for (JsonNode discount : discounts) {
            if (discount.isObject()) {
                add(details, text(discount, "nd_description"));
                add(details, text(discount, "rd_description"));
            }
        }
    }

    /**
     * Tries to extract a promotional price from discounts.
     */
    private Long extractPromotionalPrice(JsonNode discounts) {
        if (discounts == null || !discounts.isArray()) return null;
        for (JsonNode discount : discounts) {
            if (discount.isObject()) {
                String promoValue = text(discount, "nd_value");
                if (promoValue != null) {
                    Long parsed = parsePriceToMinorUnits(promoValue);
                    if (parsed != null) return parsed;
                }
                promoValue = text(discount, "rd_value");
                if (promoValue != null) {
                    Long parsed = parsePriceToMinorUnits(promoValue);
                    if (parsed != null) return parsed;
                }
            }
        }
        return null;
    }

    /**
     * Parses structured discount information from deal_discounts_json.
     */
    private List<Map<String, String>> parseStructuredDiscounts(JsonNode discounts) {
        List<Map<String, String>> result = new ArrayList<>();
        if (discounts == null || discounts.isNull() || !discounts.isArray()) {
            return result;
        }
        for (JsonNode discount : discounts) {
            if (discount.isObject()) {
                Map<String, String> discountMap = new LinkedHashMap<>();
                addToMapIfNotBlank(discountMap, "id", text(discount, "nd_id"));
                addToMapIfNotBlank(discountMap, "type", text(discount, "nd_type_id"));
                addToMapIfNotBlank(discountMap, "description", text(discount, "nd_description"));
                addToMapIfNotBlank(discountMap, "value", text(discount, "nd_value"));
                addToMapIfNotBlank(discountMap, "durationMonths", text(discount, "nd_duration_in_months"));
                addToMapIfNotBlank(discountMap, "paymentType", text(discount, "nd_payment_type_id"));
                addToMapIfNotBlank(discountMap, "source", "network");
                
                // Also check retailer discounts
                if (discountMap.isEmpty()) {
                    addToMapIfNotBlank(discountMap, "id", text(discount, "rd_id"));
                    addToMapIfNotBlank(discountMap, "type", text(discount, "rd_type_id"));
                    addToMapIfNotBlank(discountMap, "description", text(discount, "rd_description"));
                    addToMapIfNotBlank(discountMap, "value", text(discount, "rd_value"));
                    addToMapIfNotBlank(discountMap, "durationMonths", text(discount, "rd_duration_in_months"));
                    addToMapIfNotBlank(discountMap, "paymentType", text(discount, "rd_payment_type_id"));
                    addToMapIfNotBlank(discountMap, "source", "retailer");
                }
                
                if (!discountMap.isEmpty()) {
                    result.add(discountMap);
                }
            }
        }
        return result;
    }

    /**
     * Determines the offer type based on product_type, tariff_type, and other fields.
     */
    private String determineOfferType(String productType, String tariffType, JsonNode deviceProduct, JsonNode costs) {
        if (productType == null && tariffType == null) {
            return "UNKNOWN";
        }
        
        // Check product type first (1=Mobile Phone, 2=SIM Card, 3=Tablet, etc.)
        if (productType != null) {
            switch (productType.toUpperCase()) {
                case "SIM CARD", "2":
                    return "SIM_ONLY";
                case "MOBILE PHONE", "1":
                case "TABLET", "3":
                case "USB MODEM", "4":
                case "LAPTOP", "5":
                case "MOBILE WI-FI", "6":
                case "GAME CONSOLE", "7":
                case "SMARTWATCH", "8":
                    if (hasDeviceFinancing(costs)) {
                        return "PHONE_WITH_FINANCING";
                    }
                    return "PHONE_CONTRACT";
                default:
            }
        }
        
        // Check tariff type
        if (tariffType != null) {
            switch (tariffType) {
                case "1":
                case "2":
                    return "MOBILE_BROADBAND";
                case "4":
                    return "PHONE_CONTRACT";
                case "5":
                    return "PAYG";
                case "11":
                    return "SIM_ONLY";
            }
        }
        
        return "UNKNOWN";
    }

    /**
     * Checks if the offer includes device financing based on cost fields.
     */
    private boolean hasDeviceFinancing(JsonNode costs) {
        if (costs == null || costs.isNull()) {
            return false;
        }
        return text(costs, "monthly_device_inc_vat") != null 
                || text(costs, "monthly_device_exc_vat") != null
                || text(costs, "upfront_inc_vat") != null
                || text(costs, "upfront_exc_vat") != null;
    }

    /**
     * Determines if this is a SIM-only offer.
     */
    private boolean isSimOnlyOffer(String productType, String tariffType, JsonNode deviceProduct, 
                                    String monthlyDeviceIncVat, String upfrontIncVat) {
        boolean isSIM = (productType != null && (productType.equals("2") || productType.equalsIgnoreCase("SIM Card"))) 
                || (tariffType != null && tariffType.equals("11"));
        boolean hasDeviceCosts = monthlyDeviceIncVat != null || upfrontIncVat != null;
        return isSIM && !hasDeviceCosts;
    }

    /**
     * Builds a human-readable price text from the available cost components.
     */
    private String buildPriceText(String monthlyTotalIncVat, String monthlyDeviceIncVat, 
                                   String upfrontIncVat, String currency) {
        List<String> parts = new ArrayList<>();
        
        if (upfrontIncVat != null) {
            parts.add(upfrontIncVat + (currency != null ? " " + currency : "") + " (upfront)");
        }
        
        if (monthlyTotalIncVat != null) {
            parts.add(monthlyTotalIncVat + (currency != null ? " " + currency : "") + "/month");
        } else if (monthlyDeviceIncVat != null) {
            parts.add(monthlyDeviceIncVat + (currency != null ? " " + currency : "") + "/month (device)");
        }
        
        return parts.isEmpty() ? null : String.join(" + ", parts);
    }

    /**
     * Converts a price string to minor units (centimes).
     * Handles various formats: "29.99", "29,99", "29.99 EUR", etc.
     * Returns null if the price cannot be parsed.
     */
    private static Long parsePriceToMinorUnits(String priceText) {
        if (priceText == null || priceText.isBlank()) {
            return null;
        }
        
        try {
            // Remove any currency symbols and whitespace
            String cleaned = priceText.replaceAll("[^\\d.,-]", "").trim();
            // Replace comma with dot for decimal parsing
            cleaned = cleaned.replace(",", ".");
            BigDecimal amount = new BigDecimal(cleaned);
            return amount.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /**
     * Adds a non-null value to a map.
     */
    private static void addToMapIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) values.add(value);
    }
}
