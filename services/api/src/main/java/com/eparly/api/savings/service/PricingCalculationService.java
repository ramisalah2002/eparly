package com.eparly.api.savings.service;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Calcule des estimations proratisées en centimes; les cycles réellement facturés ne sont pas simulés.
 */
@Service
public class PricingCalculationService {

    public Long calculateTotalCost(Long amountMinor, OfferBillingPeriod period, Integer horizonMonths) {
        if (!isValidInput(amountMinor, period, horizonMonths)) return null;
        try {
            return totalCostMinor(BigInteger.valueOf(amountMinor), period, horizonMonths);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public BigDecimal calculateMonthlyEquivalent(Long amountMinor, OfferBillingPeriod period) {
        if (amountMinor == null || amountMinor < 0 || !isKnownPeriod(period)) return null;
        try {
            BillingRatio ratio = billingRatio(period);
            return new BigDecimal(BigInteger.valueOf(amountMinor).multiply(ratio.numerator()))
                    .divide(new BigDecimal(ratio.denominator()), 4, RoundingMode.HALF_UP);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public BigDecimal calculateAnnualEquivalent(Long amountMinor, OfferBillingPeriod period) {
        if (amountMinor == null || amountMinor < 0 || !isKnownPeriod(period)) return null;
        try {
            BillingRatio ratio = billingRatio(period);
            BigInteger annualNumerator = BigInteger.valueOf(amountMinor)
                    .multiply(ratio.numerator()).multiply(BigInteger.valueOf(12));
            return new BigDecimal(annualNumerator)
                    .divide(new BigDecimal(ratio.denominator()), 4, RoundingMode.HALF_UP);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public Long calculateTotalCostWithPromotion(
            Long normalPrice, OfferBillingPeriod period,
            Long promotionPrice, Integer promotionDurationMonths,
            Integer horizonMonths
    ) {
        if (!isValidInput(normalPrice, period, horizonMonths)) return null;
        if (promotionPrice == null || promotionDurationMonths == null || promotionDurationMonths <= 0) {
            return calculateTotalCost(normalPrice, period, horizonMonths);
        }
        if (promotionPrice < 0) return null;

        int promoMonths = Math.min(promotionDurationMonths, horizonMonths);
        int normalMonths = horizonMonths - promoMonths;
        try {
            BigInteger weightedAmount = BigInteger.valueOf(promotionPrice)
                    .multiply(BigInteger.valueOf(promoMonths))
                    .add(BigInteger.valueOf(normalPrice).multiply(BigInteger.valueOf(normalMonths)));
            return totalCostMinor(weightedAmount, period, 1);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public Long calculateEffectiveMonthlyCostWithPromotion(
            Long normalPrice, OfferBillingPeriod period,
            Long promotionPrice, Integer promotionDurationMonths,
            Integer horizonMonths
    ) {
        Long total = calculateTotalCostWithPromotion(
                normalPrice, period, promotionPrice, promotionDurationMonths, horizonMonths);
        if (total == null || horizonMonths == null || horizonMonths <= 0) return null;
        try {
            return BigDecimal.valueOf(total)
                    .divide(BigDecimal.valueOf(horizonMonths), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public boolean isSafeAmount(Long amount) {
        return amount != null && amount >= 0 && amount < Long.MAX_VALUE / 1000;
    }

    private boolean isValidInput(Long amountMinor, OfferBillingPeriod period, Integer horizonMonths) {
        return amountMinor != null && amountMinor >= 0 && horizonMonths != null
                && horizonMonths > 0 && isKnownPeriod(period);
    }

    private boolean isKnownPeriod(OfferBillingPeriod period) {
        return period != null && period != OfferBillingPeriod.UNKNOWN;
    }

    private Long totalCostMinor(BigInteger weightedMonthlyAmount, OfferBillingPeriod period, int months) {
        BillingRatio ratio = billingRatio(period);
        BigInteger numerator = weightedMonthlyAmount.multiply(BigInteger.valueOf(months))
                .multiply(ratio.numerator());
        return new BigDecimal(numerator)
                .divide(new BigDecimal(ratio.denominator()), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BillingRatio billingRatio(OfferBillingPeriod period) {
        return switch (period) {
            case WEEKLY -> new BillingRatio(BigInteger.valueOf(52), BigInteger.valueOf(12));
            case MONTHLY -> new BillingRatio(BigInteger.ONE, BigInteger.ONE);
            case QUARTERLY -> new BillingRatio(BigInteger.ONE, BigInteger.valueOf(3));
            case YEARLY -> new BillingRatio(BigInteger.ONE, BigInteger.valueOf(12));
            case UNKNOWN -> throw new IllegalArgumentException("Unknown billing period");
        };
    }

    private record BillingRatio(BigInteger numerator, BigInteger denominator) { }
}
