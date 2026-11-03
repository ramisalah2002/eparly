package com.eparly.api.catalogsync.normalization;

import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogPriceKind;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferSector;

import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record NormalizedCatalogOffer(
        String externalKey,
        String canonicalUrl,
        String name,
        String description,
        OfferSector sector,
        Long amountMinor,
        String currency,
        OfferBillingPeriod billingPeriod,
        String priceText,
        boolean startingAtPrice,
        String promotionText,
        String eligibility,
        Map<String, Object> characteristics,
        List<String> conditions,
        double confidence,
        CatalogObservationStatus validationStatus,
        List<String> validationReasons,
        String providerName,
        String country,
        CatalogPriceKind priceKind,
        Long promotionAmountMinor,
        LocalDate promotionValidUntil,
        Integer promotionDurationMonths,
        boolean promotionExpired,
        Integer commitmentMonths,
        Long activationFeeMinor,
        Boolean taxesIncluded,
        Map<String, String> priceComponents,
        LocalDate validFrom,
        LocalDate validUntil,
        CatalogComparability comparability,
        Map<String, Object> rawPayload
) {
    /** Contenu commercial servant a l'empreinte ; le rawPayload et la confiance n'en font pas partie. */
    public Map<String, Object> asMap() {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("externalKey", externalKey);
        normalized.put("canonicalUrl", canonicalUrl);
        normalized.put("name", name);
        normalized.put("description", description);
        normalized.put("sector", sector == null ? null : sector.name());
        normalized.put("amountMinor", amountMinor);
        normalized.put("currency", currency);
        normalized.put("billingPeriod", billingPeriod == null ? null : billingPeriod.name());
        normalized.put("priceText", priceText);
        normalized.put("startingAtPrice", startingAtPrice);
        normalized.put("promotionText", promotionText);
        normalized.put("eligibility", eligibility);
        normalized.put("characteristics", characteristics == null ? Map.of() : characteristics);
        normalized.put("conditions", conditions == null ? List.of() : conditions);
        normalized.put("providerName", providerName);
        normalized.put("country", country);
        normalized.put("priceKind", priceKind == null ? null : priceKind.name());
        normalized.put("promotionAmountMinor", promotionAmountMinor);
        normalized.put("promotionValidUntil", promotionValidUntil == null ? null : promotionValidUntil.toString());
        normalized.put("promotionDurationMonths", promotionDurationMonths);
        normalized.put("promotionExpired", promotionExpired);
        normalized.put("commitmentMonths", commitmentMonths);
        normalized.put("activationFeeMinor", activationFeeMinor);
        normalized.put("taxesIncluded", taxesIncluded);
        normalized.put("priceComponents", priceComponents == null ? Map.of() : priceComponents);
        normalized.put("validFrom", validFrom == null ? null : validFrom.toString());
        normalized.put("validUntil", validUntil == null ? null : validUntil.toString());
        return normalized;
    }
}
