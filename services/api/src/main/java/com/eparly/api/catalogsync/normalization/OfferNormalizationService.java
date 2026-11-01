package com.eparly.api.catalogsync.normalization;

import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Service
public class OfferNormalizationService {
    private final PriceNormalizationService prices;

    public OfferNormalizationService(PriceNormalizationService prices) {
        this.prices = prices;
    }

    public NormalizedCatalogOffer normalize(RawCatalogOffer raw, String officialDomain) {
        String name = trimToNull(raw.name());
        String url = canonicalUrl(raw.canonicalUrl(), officialDomain);
        String externalKey = trimToNull(raw.externalKey());
        if (externalKey == null) {
            externalKey = url != null ? url : name;
        }
        if (externalKey == null) {
            String fallback = String.join("|", String.valueOf(raw.name()), String.valueOf(raw.canonicalUrl()),
                    String.valueOf(raw.priceText()), String.valueOf(raw.description()));
            externalKey = "unidentified:" + java.util.UUID.nameUUIDFromBytes(
                    fallback.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        PriceNormalizationService.PriceValue price =
                prices.normalize(raw.priceText(), raw.currency(), raw.billingPeriod());
        List<String> reasons = new ArrayList<>();
        if (name == null) reasons.add("OFFER_NAME_MISSING");
        if (url == null) reasons.add("CANONICAL_URL_MISSING_OR_OUTSIDE_AUTHORIZED_DOMAIN");
        if (price.amountMinor() == null) reasons.add("PRICE_MISSING_OR_AMBIGUOUS");
        if (price.ambiguous()) reasons.add("MULTIPLE_CURRENCY_AMOUNTS");
        if (price.currency() == null) reasons.add("CURRENCY_UNKNOWN");
        if (price.billingPeriod() == null || price.billingPeriod() == OfferBillingPeriod.UNKNOWN) {
            reasons.add("BILLING_PERIOD_UNKNOWN");
        }
        if (price.startingAtPrice()) reasons.add("PRICE_IS_A_NON_GUARANTEED_FROM_PRICE");
        if (raw.promotionText() != null && !raw.promotionText().isBlank()) {
            reasons.add("PROMOTION_REQUIRES_HUMAN_REVIEW");
        }
        if (raw.eligibility() == null || raw.eligibility().isBlank()) {
            reasons.add("ELIGIBILITY_NOT_STATED");
        }

        double confidence = 0.20;
        if (name != null) confidence += 0.20;
        if (url != null) confidence += 0.15;
        if (price.amountMinor() != null) confidence += 0.20;
        if (price.currency() != null) confidence += 0.10;
        if (price.billingPeriod() != null) confidence += 0.10;
        if (raw.sector() != null) confidence += 0.05;
        CatalogObservationStatus status = reasons.stream().anyMatch(reason ->
                !reason.equals("ELIGIBILITY_NOT_STATED"))
                ? CatalogObservationStatus.NEEDS_REVIEW
                : CatalogObservationStatus.VALIDATED_STRUCTURALLY;
        if (name == null || url == null) status = CatalogObservationStatus.REJECTED;

        return new NormalizedCatalogOffer(externalKey, url, name, trimToNull(raw.description()),
                raw.sector(), price.amountMinor(), price.currency(), price.billingPeriod(),
                trimToNull(raw.priceText()), price.startingAtPrice(), trimToNull(raw.promotionText()),
                trimToNull(raw.eligibility()), new TreeMap<>(raw.characteristics() == null
                ? java.util.Map.of() : raw.characteristics()),
                raw.conditions() == null ? List.of() : List.copyOf(raw.conditions()),
                Math.min(1.0, confidence), status, List.copyOf(reasons));
    }

    public NormalizedCatalogOffer unsupportedPage(String url, String officialDomain,
                                                  com.eparly.api.offer.domain.OfferSector sector) {
        String identity = "page:" + java.util.UUID.nameUUIDFromBytes(
                url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        NormalizedCatalogOffer extracted = normalize(new RawCatalogOffer(identity, url, null, null,
                null, null, null, null, null, java.util.Map.of(), List.of(), sector), officialDomain);
        return new NormalizedCatalogOffer(extracted.externalKey(), extracted.canonicalUrl(), null, null,
                sector, null, null, null, null, false, null, null, java.util.Map.of(), List.of(),
                0.05, com.eparly.api.catalogsync.domain.CatalogObservationStatus.UNSUPPORTED,
                List.of("NO_SUPPORTED_COMMERCIAL_DATA_EXTRACTED"));
    }

    private static String canonicalUrl(String value, String officialDomain) {
        if (value == null || officialDomain == null) return null;
        try {
            URI uri = URI.create(value.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getHost().endsWith(".")) return null;
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            String domain = officialDomain.toLowerCase(java.util.Locale.ROOT);
            if (!host.equals(domain) && !host.endsWith("." + domain)) return null;
            return new URI("https", null, host, uri.getPort(), uri.getPath(), uri.getQuery(), null).toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
