package com.eparly.api.catalogsync.normalization;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogPriceKind;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.extraction.StructuredOfferData;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OfferNormalizationService {
    /** Motifs informatifs : ils n'empechent pas le statut VALIDATED_STRUCTURALLY. */
    private static final String ELIGIBILITY_NOT_STATED = "ELIGIBILITY_NOT_STATED";
    private static final String COMPARABILITY_PREFIX = "COMPARABILITY_ATTRIBUTE_MISSING:";

    private final PriceNormalizationService prices;
    private final CatalogCategorySpecs categorySpecs;
    private final Clock clock;
    private final ZoneId zone;

    public OfferNormalizationService(PriceNormalizationService prices, CatalogCategorySpecs categorySpecs,
                                     CatalogSyncProperties properties, Clock clock) {
        this.prices = prices;
        this.categorySpecs = categorySpecs;
        this.clock = clock;
        this.zone = ZoneId.of(properties.getZone());
    }

    public NormalizedCatalogOffer normalize(RawCatalogOffer raw, String officialDomain) {
        return normalize(raw, List.of(officialDomain == null ? "" : officialDomain));
    }

    public NormalizedCatalogOffer normalize(RawCatalogOffer raw, List<String> allowedDomains) {
        StructuredOfferData structured = raw.structured();
        String name = trimToNull(raw.name());
        String url = canonicalUrl(raw.canonicalUrl(), allowedDomains);
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
        Long amountMinor = price.amountMinor();
        if (structured != null && structured.standardAmountMinor() != null) {
            amountMinor = structured.standardAmountMinor();
        }
        if (amountMinor != null && amountMinor < 0) {
            reasons.add("PRICE_NEGATIVE");
            amountMinor = null;
        }

        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate promotionUntil = structured == null ? null : structured.promotionValidUntil();
        Long promotionAmount = structured == null ? null : structured.promotionAmountMinor();
        String promotionText = trimToNull(raw.promotionText());
        boolean promotionDeclared = promotionText != null || promotionAmount != null;
        boolean promotionExpired = promotionDeclared && promotionUntil != null && promotionUntil.isBefore(today);

        CatalogPriceKind kind = priceKind(structured, amountMinor, raw.priceText(),
                promotionDeclared && !promotionExpired);
        if (name == null) reasons.add("OFFER_NAME_MISSING");
        if (url == null) reasons.add("CANONICAL_URL_MISSING_OR_OUTSIDE_AUTHORIZED_DOMAIN");
        if (amountMinor == null) reasons.add("PRICE_MISSING_OR_AMBIGUOUS");
        if (price.ambiguous()) reasons.add("MULTIPLE_CURRENCY_AMOUNTS");
        if (price.currency() == null) reasons.add("CURRENCY_UNKNOWN");
        if (price.billingPeriod() == null || price.billingPeriod() == OfferBillingPeriod.UNKNOWN) {
            reasons.add("BILLING_PERIOD_UNKNOWN");
        }
        if (price.startingAtPrice()) reasons.add("PRICE_IS_A_NON_GUARANTEED_FROM_PRICE");
        if (kind == CatalogPriceKind.VARIABLE) reasons.add("PRICE_DEPENDS_ON_USAGE");
        if (kind == CatalogPriceKind.PROFILE_DEPENDENT) reasons.add("PRICE_DEPENDS_ON_PROFILE");
        if (promotionExpired) {
            reasons.add("PROMOTION_EXPIRED");
        } else if (promotionDeclared) {
            reasons.add("PROMOTION_REQUIRES_HUMAN_REVIEW");
        }
        if (promotionAmount != null && amountMinor != null && promotionAmount >= amountMinor) {
            reasons.add("PROMOTION_PRICE_NOT_BELOW_STANDARD_PRICE");
        }
        if (structured != null && structured.validUntil() != null && structured.validUntil().isBefore(today)) {
            reasons.add("OFFER_VALIDITY_EXPIRED");
        }
        if (raw.sector() == null) reasons.add("SECTOR_UNKNOWN");
        if (raw.eligibility() == null || raw.eligibility().isBlank()) {
            reasons.add(ELIGIBILITY_NOT_STATED);
        }

        Map<String, Object> characteristics = new TreeMap<>(raw.characteristics() == null
                ? Map.of() : raw.characteristics());
        Map<String, Object> comparabilityAttributes = new TreeMap<>(characteristics);
        Integer commitmentMonths = structured == null ? null : structured.commitmentMonths();
        if (commitmentMonths != null) comparabilityAttributes.put("commitmentMonths", commitmentMonths);
        CatalogComparability comparability = categorySpecs.evaluate(raw.sector(), comparabilityAttributes);
        comparability.missingAttributes().forEach(attribute -> reasons.add(COMPARABILITY_PREFIX + attribute));

        double confidence = 0.20;
        if (name != null) confidence += 0.20;
        if (url != null) confidence += 0.15;
        if (amountMinor != null) confidence += 0.20;
        if (price.currency() != null) confidence += 0.10;
        if (price.billingPeriod() != null) confidence += 0.10;
        if (raw.sector() != null) confidence += 0.05;
        CatalogObservationStatus status = reasons.stream().anyMatch(reason ->
                !reason.equals(ELIGIBILITY_NOT_STATED) && !reason.startsWith(COMPARABILITY_PREFIX))
                ? CatalogObservationStatus.NEEDS_REVIEW
                : CatalogObservationStatus.VALIDATED_STRUCTURALLY;
        if (name == null || url == null) status = CatalogObservationStatus.REJECTED;

        return new NormalizedCatalogOffer(externalKey, url, name, trimToNull(raw.description()),
                raw.sector(), amountMinor, price.currency(), price.billingPeriod(),
                trimToNull(raw.priceText()), price.startingAtPrice(), promotionText,
                trimToNull(raw.eligibility()), characteristics,
                raw.conditions() == null ? List.of() : List.copyOf(raw.conditions()),
                Math.min(1.0, confidence), status, List.copyOf(reasons),
                structured == null ? null : trimToNull(structured.providerName()),
                structured == null || trimToNull(structured.country()) == null
                        ? "FR" : structured.country().trim().toUpperCase(Locale.ROOT),
                kind, promotionAmount, promotionUntil,
                structured == null ? null : structured.promotionDurationMonths(), promotionExpired,
                commitmentMonths, structured == null ? null : structured.activationFeeMinor(),
                structured == null ? null : structured.taxesIncluded(),
                structured == null || structured.priceComponents() == null
                        ? Map.of() : new TreeMap<>(structured.priceComponents()),
                structured == null ? null : structured.validFrom(),
                structured == null ? null : structured.validUntil(),
                comparability, structured == null ? null : structured.rawPayload());
    }

    public NormalizedCatalogOffer unsupportedPage(String url, String officialDomain,
                                                  com.eparly.api.offer.domain.OfferSector sector) {
        String identity = "page:" + java.util.UUID.nameUUIDFromBytes(
                url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String canonical = canonicalUrl(url, List.of(officialDomain == null ? "" : officialDomain));
        return new NormalizedCatalogOffer(identity, canonical, null, null,
                sector, null, null, null, null, false, null, null, Map.of(), List.of(),
                0.05, CatalogObservationStatus.UNSUPPORTED,
                List.of("NO_SUPPORTED_COMMERCIAL_DATA_EXTRACTED"),
                null, "FR", CatalogPriceKind.MISSING, null, null, null, false, null, null, null,
                Map.of(), null, null,
                new CatalogComparability(CatalogComparability.Status.NOT_COMPARABLE, null, List.of()), null);
    }

    private static CatalogPriceKind priceKind(StructuredOfferData structured, Long amountMinor, String priceText,
                                              boolean promotionActive) {
        CatalogPriceKind declared = structured == null ? null : structured.priceKind();
        if (declared == CatalogPriceKind.VARIABLE || declared == CatalogPriceKind.PROFILE_DEPENDENT) return declared;
        if (amountMinor == null) {
            if (declared == null && priceText != null) {
                String text = priceText.toLowerCase(Locale.ROOT);
                if (text.matches("(?s).*(sur devis|selon (votre )?profil|sur mesure|personnalis).*")) {
                    return CatalogPriceKind.PROFILE_DEPENDENT;
                }
                if (text.matches("(?s).*(prix variable|tarif variable|index[ée]).*")) {
                    return CatalogPriceKind.VARIABLE;
                }
            }
            return CatalogPriceKind.MISSING;
        }
        if (promotionActive) return CatalogPriceKind.PROMOTIONAL;
        return amountMinor == 0 ? CatalogPriceKind.FREE : CatalogPriceKind.FIXED;
    }

    private static String canonicalUrl(String value, List<String> allowedDomains) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getHost().endsWith(".")) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            // "*" : agregateur ouvert citant les pages officielles des fournisseurs ; HTTPS reste exige.
            boolean allowed = allowedDomains.contains("*") || allowedDomains.stream().filter(domain -> domain != null && !domain.isBlank())
                    .map(domain -> domain.toLowerCase(Locale.ROOT))
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
            if (!allowed) return null;
            return new URI("https", null, host, uri.getPort(), uri.getPath(), uri.getQuery(), null).toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
