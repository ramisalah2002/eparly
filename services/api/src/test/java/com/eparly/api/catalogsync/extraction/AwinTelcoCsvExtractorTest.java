package com.eparly.api.catalogsync.extraction;

import com.eparly.api.catalogsync.normalization.OfferNormalizationService;
import com.eparly.api.catalogsync.normalization.PriceNormalizationService;
import com.eparly.api.offer.domain.OfferSector;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwinTelcoCsvExtractorTest {
    private final AwinTelcoCsvExtractor extractor = new AwinTelcoCsvExtractor(new ObjectMapper());
    private final OfferNormalizationService normalization =
            new OfferNormalizationService(new PriceNormalizationService(),
            new com.eparly.api.catalogsync.normalization.CatalogCategorySpecs(),
            new com.eparly.api.catalogsync.CatalogSyncProperties(), java.time.Clock.systemUTC());

    @Test
    void extractsDocumentedAwinTelcoFieldsWithoutTreatingPromotionsAsVerifiedPrices() {
        var offers = extractor.extract(csv("""
                "merchant_product_id","merchant_deep_link","product_name","description","currency","last_updated","terms_of_contract","product_type","promotional_text","network_details_json","tariff_details_json","tariff_allowances_json","deal_cost_json","deal_discounts_json"
                "offer-42","https://merchant.example.fr/mobile","Forfait mobile 100 Go","Description française","EUR","2026-10-28 08:15:00","See terms","Consumer","Offre temporaire","{""name"":""Réseau Exemple""}","{""tariff_name"":""100 Go"",""tariff_term"":""12"",""tariff_promo_info"":""12.99 EUR for 6 months""}","{""data_details"":{""cellular_mb"":""102400"",""cellular_speed"":""5G""}}","{""monthly_total_inc_vat"":""12.99"",""monthly_contract_term_months"":12}","{""network_discount"":{""nd_description"":""12.99 EUR for 6 months"",""nd_duration_in_months"":6}}"
                """), OfferSector.MOBILE_PLAN);

        assertThat(offers).hasSize(1);
        RawCatalogOffer offer = offers.getFirst();
        assertThat(offer.externalKey()).isEqualTo("offer-42");
        assertThat(offer.canonicalUrl()).isEqualTo("https://merchant.example.fr/mobile");
        assertThat(offer.priceText()).isEqualTo("12.99 EUR");
        assertThat(offer.currency()).isEqualTo("EUR");
        assertThat(offer.billingPeriod()).isEqualTo("MONTHLY");
        assertThat(offer.promotionText()).contains("12.99 EUR for 6 months");
        assertThat(offer.characteristics()).containsEntry("mobileDataMb", "102400")
                .containsEntry("mobileDataSpeed", "5G")
                .containsEntry("commitmentMonths", "12")
                .containsEntry("sourceLastUpdated", "2026-10-28 08:15:00");

        var normalized = normalization.normalize(offer, "merchant.example.fr");
        assertThat(normalized.amountMinor()).isEqualTo(1299L);
        assertThat(normalized.billingPeriod()).isEqualTo(com.eparly.api.offer.domain.OfferBillingPeriod.MONTHLY);
        assertThat(normalized.validationStatus().name()).isEqualTo("NEEDS_REVIEW");
        assertThat(normalized.validationReasons()).contains("PROMOTION_REQUIRES_HUMAN_REVIEW",
                "ELIGIBILITY_NOT_STATED");
    }

    @Test
    void preservesStableFeedIdentityAcrossPriceChangesAndLeavesMissingPricingUnknown() {
        String oldRow = """
                "merchant_product_id","merchant_deep_link","product_name","currency","deal_cost_json"
                "offer-stable","https://merchant.example.fr/plan","Plan","EUR","{""monthly_total_inc_vat"":""10.00""}"
                """;
        String changedRow = oldRow.replace("10.00", "11.00");
        String missingPrice = """
                "merchant_product_id","merchant_deep_link","product_name","currency","deal_cost_json"
                "offer-stable","https://merchant.example.fr/plan","Plan","EUR","{}"
                """;

        RawCatalogOffer first = extractor.extract(oldRow, OfferSector.MOBILE_PLAN).getFirst();
        RawCatalogOffer changed = extractor.extract(changedRow, OfferSector.MOBILE_PLAN).getFirst();
        RawCatalogOffer unknown = extractor.extract(missingPrice, OfferSector.MOBILE_PLAN).getFirst();

        assertThat(first.externalKey()).isEqualTo(changed.externalKey());
        assertThat(first.priceText()).isEqualTo("10.00 EUR");
        assertThat(changed.priceText()).isEqualTo("11.00 EUR");
        assertThat(unknown.priceText()).isNull();
        assertThat(normalization.normalize(unknown, "merchant.example.fr").amountMinor()).isNull();
    }

    @Test
    void rejectsMalformedRowsJsonAndNonMobileSectors() {
        assertThatThrownBy(() -> extractor.extract("""
                merchant_product_id,merchant_deep_link,product_name
                ,https://merchant.example.fr/plan,Plan
                """, OfferSector.MOBILE_PLAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing its product ID");
        assertThatThrownBy(() -> extractor.extract("""
                merchant_product_id,merchant_deep_link,product_name,deal_cost_json
                id,https://merchant.example.fr/plan,Plan,not-json
                """, OfferSector.MOBILE_PLAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed JSON");
        assertThatThrownBy(() -> extractor.extract("id,url,name\n1,https://example.test,p", OfferSector.STREAMING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supported for mobile plans");
    }

    private static String csv(String body) {
        return body.stripIndent().strip();
    }
}
