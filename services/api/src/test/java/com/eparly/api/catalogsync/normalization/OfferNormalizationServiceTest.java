package com.eparly.api.catalogsync.normalization;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogObservationStatus;
import com.eparly.api.catalogsync.domain.CatalogPriceKind;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.extraction.StructuredOfferData;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferSector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfferNormalizationServiceTest {
    private static final String URL = "https://offers.example.fr/plan";
    private final CatalogCategorySpecs specs = new CatalogCategorySpecs();
    private final OfferNormalizationService service = new OfferNormalizationService(new PriceNormalizationService(),
            specs, new CatalogSyncProperties(),
            Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("Europe/Paris")));

    @Test
    void distinguishesMissingZeroMonthlyAnnualVariableAndProfilePrices() {
        var missing = normalize(raw(null, "EUR", "MONTHLY", null));
        assertThat(missing.amountMinor()).isNull();
        assertThat(missing.priceKind()).isEqualTo(CatalogPriceKind.MISSING);
        assertThat(missing.validationReasons()).contains("PRICE_MISSING_OR_AMBIGUOUS");

        var zero = normalize(raw("0,00 €", "EUR", "MONTHLY", null));
        assertThat(zero.amountMinor()).isZero();
        assertThat(zero.priceKind()).isEqualTo(CatalogPriceKind.FREE);

        var free = normalize(raw("Gratuit", "EUR", "MONTHLY", null));
        assertThat(free.amountMinor()).isZero();
        assertThat(free.priceKind()).isEqualTo(CatalogPriceKind.FREE);

        var monthly = normalize(raw("9,99 € par mois", null, null, null));
        assertThat(monthly.amountMinor()).isEqualTo(999L);
        assertThat(monthly.billingPeriod()).isEqualTo(OfferBillingPeriod.MONTHLY);
        assertThat(monthly.priceKind()).isEqualTo(CatalogPriceKind.FIXED);

        var annual = normalize(raw("99,99 € par an", null, null, null));
        assertThat(annual.amountMinor()).isEqualTo(9999L);
        assertThat(annual.billingPeriod()).isEqualTo(OfferBillingPeriod.YEARLY);

        var variable = normalize(raw("Tarif variable", "EUR", "MONTHLY", null));
        assertThat(variable.amountMinor()).isNull();
        assertThat(variable.priceKind()).isEqualTo(CatalogPriceKind.VARIABLE);
        assertThat(variable.validationReasons()).contains("PRICE_DEPENDS_ON_USAGE");

        var profile = normalize(raw("Sur devis", "EUR", "MONTHLY", null));
        assertThat(profile.amountMinor()).isNull();
        assertThat(profile.priceKind()).isEqualTo(CatalogPriceKind.PROFILE_DEPENDENT);
        assertThat(profile.validationReasons()).contains("PRICE_DEPENDS_ON_PROFILE");
    }

    @Test
    void textualAmountsAreNeverTruncatedOrSigned() {
        PriceNormalizationService prices = new PriceNormalizationService();

        assertThat(prices.normalize("0,1985 € le kWh", null, null).amountMinor()).isNull();
        assertThat(prices.normalize("-5,00 € par mois", null, null).amountMinor()).isNull();
        assertThat(prices.normalize("1 mois offert puis 9,99 € / mois", null, null).amountMinor()).isEqualTo(999L);
        assertThat(prices.normalize("1 299,00 € / an", null, null).amountMinor()).isEqualTo(129900L);
    }

    @Test
    void activeAndExpiredPromotionsAreKeptApartWithoutOverwritingTheStandardPrice() {
        var active = normalize(structured(new StructuredOfferData("Fournisseur", null, 1999L, 999L,
                LocalDate.parse("2026-12-31"), 6, 12, 1900L, null, Boolean.TRUE, Map.of(), null, null, null)));
        assertThat(active.priceKind()).isEqualTo(CatalogPriceKind.PROMOTIONAL);
        assertThat(active.amountMinor()).isEqualTo(1999L);
        assertThat(active.promotionAmountMinor()).isEqualTo(999L);
        assertThat(active.promotionExpired()).isFalse();
        assertThat(active.commitmentMonths()).isEqualTo(12);
        assertThat(active.activationFeeMinor()).isEqualTo(1900L);
        assertThat(active.validationReasons()).contains("PROMOTION_REQUIRES_HUMAN_REVIEW");

        var expired = normalize(structured(new StructuredOfferData("Fournisseur", null, 1999L, 999L,
                LocalDate.parse("2026-10-31"), 6, null, null, null, null, Map.of(), null, null, null)));
        assertThat(expired.priceKind()).isEqualTo(CatalogPriceKind.FIXED);
        assertThat(expired.amountMinor()).isEqualTo(1999L);
        assertThat(expired.promotionExpired()).isTrue();
        assertThat(expired.validationStatus()).isEqualTo(CatalogObservationStatus.NEEDS_REVIEW);
        assertThat(expired.validationReasons()).contains("PROMOTION_EXPIRED");
    }

    @Test
    void negativeOrIncoherentStructuredPricesGoToReviewWithoutAValue() {
        var negative = normalize(structured(new StructuredOfferData("Fournisseur", null, -100L, null, null, null,
                null, null, null, null, Map.of(), null, null, null)));
        assertThat(negative.amountMinor()).isNull();
        assertThat(negative.priceKind()).isEqualTo(CatalogPriceKind.MISSING);
        assertThat(negative.validationReasons()).contains("PRICE_NEGATIVE");

        var promotionHigher = normalize(structured(new StructuredOfferData("Fournisseur", null, 999L, 1999L, null,
                null, null, null, null, null, Map.of(), null, null, null)));
        assertThat(promotionHigher.validationReasons()).contains("PROMOTION_PRICE_NOT_BELOW_STANDARD_PRICE");
    }

    @Test
    void sameCategoryDoesNotMeanComparable() {
        Map<String, Object> complete = Map.of("tariffOption", "BASE", "powerKva", "6", "zone", "FR",
                "kwhPricingKnown", true);
        var six = normalize(new RawCatalogOffer("a", URL, "Offre", null, "10 €", "EUR", "YEARLY", null, "Tous",
                complete, List.of(), OfferSector.ELECTRICITY));
        var nine = normalize(new RawCatalogOffer("b", URL, "Offre", null, "10 €", "EUR", "YEARLY", null, "Tous",
                Map.of("tariffOption", "BASE", "powerKva", "9", "zone", "FR", "kwhPricingKnown", true),
                List.of(), OfferSector.ELECTRICITY));
        assertThat(six.comparability().status()).isEqualTo(CatalogComparability.Status.COMPARABLE_WITHIN_GROUP);
        assertThat(six.comparability().comparisonGroup()).isNotEqualTo(nine.comparability().comparisonGroup());

        var incomplete = normalize(new RawCatalogOffer("c", URL, "Offre", null, "10 €", "EUR", "YEARLY", null, "Tous",
                Map.of("tariffOption", "BASE"), List.of(), OfferSector.ELECTRICITY));
        assertThat(incomplete.comparability().status()).isEqualTo(CatalogComparability.Status.INSUFFICIENT_ATTRIBUTES);
        assertThat(incomplete.comparability().comparisonGroup()).isNull();
        assertThat(incomplete.comparability().missingAttributes()).contains("powerKva", "zone", "kwhPricingKnown");
        assertThat(incomplete.validationReasons()).contains("COMPARABILITY_ATTRIBUTE_MISSING:powerKva");

        var other = normalize(new RawCatalogOffer("d", URL, "Offre", null, "10 €", "EUR", "MONTHLY", null, "Tous",
                Map.of(), List.of(), OfferSector.OTHER_RECURRING));
        assertThat(other.comparability().status()).isEqualTo(CatalogComparability.Status.NOT_COMPARABLE);
    }

    @Test
    void everyCategoryOfTheEngineHasAnExplicitComparabilityDecision() {
        for (OfferSector sector : OfferSector.values()) {
            CatalogComparability result = specs.evaluate(sector, Map.of());
            assertThat(result.status()).as(sector.name()).isIn(
                    CatalogComparability.Status.INSUFFICIENT_ATTRIBUTES, CatalogComparability.Status.NOT_COMPARABLE);
        }
    }

    @Test
    void identityIsStableAcrossLabelVariationsButKeepsVariantsApart() {
        CatalogDeduplicationService dedup = new CatalogDeduplicationService(specs);
        var first = dedup.identify(normalize(identityOffer("Électricité Plus SAS", "Offre Verte", "6")), "x");
        var sameLabels = dedup.identify(normalize(identityOffer("electricite plus", "OFFRE  verte", "6")), "x");
        var otherPower = dedup.identify(normalize(identityOffer("Electricite Plus", "Offre Verte", "9")), "x");

        assertThat(first.identityKey()).isEqualTo(sameLabels.identityKey());
        assertThat(first.identityKey()).isNotEqualTo(otherPower.identityKey());
    }

    @Test
    void offerDomainsLimitTheAcceptedCanonicalUrls() {
        var outside = service.normalize(raw("9 €", "EUR", "MONTHLY", null), List.of("autre.example.fr"));
        assertThat(outside.canonicalUrl()).isNull();
        assertThat(outside.validationStatus()).isEqualTo(CatalogObservationStatus.REJECTED);
    }

    @Test
    void priceDetectorSeparatesPriceFromPromotionAndDetails() {
        CatalogPriceChangeDetector detector = new CatalogPriceChangeDetector();
        Map<String, Object> base = Map.of("amountMinor", 999, "currency", "EUR", "promotionAmountMinor", 499);

        assertThat(detector.detect(detector.snapshot(base), detector.snapshot(base)))
                .isEqualTo(CatalogPriceChangeDetector.Change.NONE);
        assertThat(detector.detect(detector.snapshot(base), detector.snapshot(
                Map.of("amountMinor", 1099, "currency", "EUR", "promotionAmountMinor", 499))))
                .isEqualTo(CatalogPriceChangeDetector.Change.PRICE_CHANGED);
        assertThat(detector.detect(detector.snapshot(base), detector.snapshot(
                Map.of("amountMinor", 999, "currency", "EUR", "promotionAmountMinor", 399))))
                .isEqualTo(CatalogPriceChangeDetector.Change.PROMOTION_CHANGED);
        assertThat(detector.detect(detector.snapshot(base), detector.snapshot(
                Map.of("currency", "EUR", "promotionAmountMinor", 499))))
                .isEqualTo(CatalogPriceChangeDetector.Change.PRICE_CHANGED);
    }

    private NormalizedCatalogOffer normalize(RawCatalogOffer raw) {
        return service.normalize(raw, "example.fr");
    }

    private static RawCatalogOffer raw(String price, String currency, String period, String promotion) {
        return new RawCatalogOffer("key", URL, "Offre", null, price, currency, period, promotion, "Tous",
                Map.of(), List.of(), OfferSector.MOBILE_PLAN);
    }

    private static RawCatalogOffer structured(StructuredOfferData data) {
        return new RawCatalogOffer("key", URL, "Offre", null, null, "EUR", "MONTHLY", null, "Tous",
                Map.of(), List.of(), OfferSector.INTERNET_BOX, data);
    }

    private static RawCatalogOffer identityOffer(String provider, String plan, String power) {
        return new RawCatalogOffer("key", URL, plan, null, null, "EUR", "YEARLY", null, null,
                Map.of("powerKva", power), List.of(), OfferSector.ELECTRICITY,
                new StructuredOfferData(provider, null, 100L, null, null, null, null, null, null, null,
                        Map.of(), null, null, null));
    }
}
