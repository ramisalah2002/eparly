package com.eparly.api.savings.service;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.PromotionPriceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculationServiceTest {

    private final PricingCalculationService pricing = new PricingCalculationService();
    private final PromotionCalculationService promotions = new PromotionCalculationService();

    @Test
    void monthlyAndPromotionalTotalsUseExactMinorUnits() {
        assertThat(pricing.calculateTotalCost(2_000L, OfferBillingPeriod.MONTHLY, 12)).isEqualTo(24_000L);
        assertThat(pricing.calculateTotalCostWithPromotion(
                1_999L, OfferBillingPeriod.MONTHLY, 999L, 3, 12)).isEqualTo(20_988L);
        assertThat(pricing.calculateTotalCostWithPromotion(
                1_999L, OfferBillingPeriod.MONTHLY, 999L, 3, 24)).isEqualTo(44_976L);
    }

    @Test
    void supportedPeriodsUseDocumentedMonthlyProration() {
        assertThat(pricing.calculateTotalCost(500L, OfferBillingPeriod.WEEKLY, 12)).isEqualTo(26_000L);
        assertThat(pricing.calculateTotalCost(3_000L, OfferBillingPeriod.QUARTERLY, 12)).isEqualTo(12_000L);
        assertThat(pricing.calculateTotalCost(12_000L, OfferBillingPeriod.YEARLY, 24)).isEqualTo(24_000L);
        assertThat(pricing.calculateTotalCost(1L, OfferBillingPeriod.WEEKLY, 1)).isEqualTo(4L);
    }

    @Test
    void unknownPriceOrPeriodIsNotConvertedToZero() {
        assertThat(pricing.calculateTotalCost(null, OfferBillingPeriod.MONTHLY, 12)).isNull();
        assertThat(pricing.calculateTotalCost(0L, OfferBillingPeriod.UNKNOWN, 12)).isNull();
    }

    @Test
    void percentageAndFixedPromotionsHaveOneCanonicalInterpretation() {
        OfferPricing percentage = pricing(1_999L, PromotionPriceType.PERCENTAGE);
        percentage.setPromotionPercent(50);
        CommercialOffer offer = new CommercialOffer();
        assertThat(promotions.promotionPriceMinor(percentage)).isEqualTo(1_000L);
        assertThat(promotions.isPromotionApplicable(
                percentage, offer, LocalDate.now(), verifiedPromotionEvidence()))
                .isTrue();

        OfferPricing fixed = pricing(1_999L, PromotionPriceType.FIXED_PRICE);
        fixed.setPriceAfterPromotionMinor(999L);
        assertThat(promotions.promotionPriceMinor(fixed)).isEqualTo(999L);
        assertThat(promotions.isPromotionApplicable(fixed, offer, LocalDate.now(), verifiedPromotionEvidence()))
                .isTrue();

        fixed.setPromotionPercent(50);
        assertThat(promotions.isPromotionApplicable(fixed, offer, LocalDate.now(), verifiedPromotionEvidence()))
                .isFalse();
    }

    @Test
    void futureExpiredUnverifiedAndIneligiblePromotionsAreNotApplicable() {
        OfferPricing pricing = pricing(1_999L, PromotionPriceType.PERCENTAGE);
        pricing.setPromotionPercent(20);
        OfferEvidence evidence = verifiedPromotionEvidence();
        CommercialOffer offer = new CommercialOffer();

        pricing.setPromotionValidFrom(LocalDate.now().plusDays(1));
        assertThat(promotions.isPromotionApplicable(pricing, offer, LocalDate.now(), evidence)).isFalse();
        pricing.setPromotionValidFrom(null);
        pricing.setPromotionValidUntil(LocalDate.now().minusDays(1));
        assertThat(promotions.isPromotionApplicable(pricing, offer, LocalDate.now(), evidence)).isFalse();
        pricing.setPromotionValidUntil(null);
        pricing.setDiscountConditions("Nouveaux clients uniquement");
        assertThat(promotions.isPromotionApplicable(pricing, offer, LocalDate.now(), evidence)).isFalse();
        pricing.setDiscountConditions(null);
        offer.setEligibilityConditions("Nouveaux clients uniquement");
        assertThat(promotions.isPromotionApplicable(pricing, offer, LocalDate.now(), evidence)).isFalse();
        offer.setEligibilityConditions(null);
        assertThat(promotions.isPromotionApplicable(pricing, offer, LocalDate.now(), null)).isFalse();
        assertThat(promotions.isPromotionApplicable(pricing, offer, LocalDate.now(), new OfferEvidence()))
                .isFalse();
    }

    @Test
    void ambiguousLegacyPromotionsRemainUnused() {
        OfferPricing legacy = pricing(1_999L, null);
        legacy.setPromotionPercent(50);
        legacy.setPriceAfterPromotionMinor(1_999L);
        assertThat(promotions.promotionPriceMinor(legacy)).isNull();
        assertThat(promotions.isPromotionApplicable(
                legacy, new CommercialOffer(), LocalDate.now(), verifiedPromotionEvidence()))
                .isFalse();
    }

    private OfferPricing pricing(long amount, PromotionPriceType type) {
        OfferPricing pricing = new OfferPricing();
        pricing.setAmountMinor(amount);
        pricing.setPromotionPriceType(type);
        pricing.setPromotionDurationMonths(3);
        return pricing;
    }

    private OfferEvidence verifiedPromotionEvidence() {
        OfferEvidence evidence = new OfferEvidence();
        evidence.setEvidenceScope(OfferEvidenceScope.PROMOTION);
        evidence.setVerificationStatus(EvidenceStatus.VERIFIED);
        evidence.setVerifiedAt(Instant.now());
        return evidence;
    }
}
