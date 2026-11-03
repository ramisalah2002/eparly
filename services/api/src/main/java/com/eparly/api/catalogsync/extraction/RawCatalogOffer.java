package com.eparly.api.catalogsync.extraction;

import com.eparly.api.offer.domain.OfferSector;

import java.util.List;
import java.util.Map;

public record RawCatalogOffer(
        String externalKey,
        String canonicalUrl,
        String name,
        String description,
        String priceText,
        String currency,
        String billingPeriod,
        String promotionText,
        String eligibility,
        Map<String, Object> characteristics,
        List<String> conditions,
        OfferSector sector,
        StructuredOfferData structured
) {
    public RawCatalogOffer(String externalKey, String canonicalUrl, String name, String description,
                           String priceText, String currency, String billingPeriod, String promotionText,
                           String eligibility, Map<String, Object> characteristics, List<String> conditions,
                           OfferSector sector) {
        this(externalKey, canonicalUrl, name, description, priceText, currency, billingPeriod, promotionText,
                eligibility, characteristics, conditions, sector, null);
    }
}
