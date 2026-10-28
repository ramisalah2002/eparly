package com.eparly.api.savings.service;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.savings.dto.SubscriptionCostSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calcul monétaire exact sur un horizon donné.
 * Utilise BigDecimal pour les conversions et Long pour les montants en centimes.
 */
@Service
public class PricingCalculationService {

    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    /**
     * Calcule le coût total sur un horizon en mois à partir d'un montant récurrent et d'une périodicité.
     *
     * @param amountMontant le montant récurrent en centimes
     * @param period la périodicité de facturation
     * @param horizonMonths l'horizon en mois
     * @return le coût total en centimes, ou null si le montant est inconnu
     */
    public Long calculateTotalCost(Long amountMontant, OfferBillingPeriod period, Integer horizonMonths) {
        if (amountMontant == null || amountMontant < 0 || horizonMonths == null || horizonMonths <= 0) {
            return null;
        }
        if (period == null || period == OfferBillingPeriod.UNKNOWN) {
            return null;
        }

        BigDecimal amount = BigDecimal.valueOf(amountMontant);
        BigDecimal months = BigDecimal.valueOf(horizonMonths);

        BigDecimal multiplier;
        switch (period) {
            case WEEKLY -> multiplier = new BigDecimal("52").divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
            case MONTHLY -> multiplier = BigDecimal.ONE;
            case QUARTERLY -> multiplier = new BigDecimal("1").divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP);
            case YEARLY -> multiplier = new BigDecimal("1").divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
            default -> {
                return null;
            }
        }

        BigDecimal total = amount.multiply(months).multiply(multiplier);
        return total.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Calcule le coût mensuel équivalent à partir d'un montant récurrent et d'une périodicité.
     */
    public BigDecimal calculateMonthlyEquivalent(Long amountMontant, OfferBillingPeriod period) {
        if (amountMontant == null || amountMontant < 0 || period == null || period == OfferBillingPeriod.UNKNOWN) {
            return null;
        }
        BigDecimal amount = BigDecimal.valueOf(amountMontant);
        BigDecimal multiplier;
        switch (period) {
            case WEEKLY -> multiplier = new BigDecimal("52").divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
            case MONTHLY -> multiplier = BigDecimal.ONE;
            case QUARTERLY -> multiplier = new BigDecimal("1").divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP);
            case YEARLY -> multiplier = new BigDecimal("1").divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
            default -> {
                return null;
            }
        }
        return amount.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le coût annuel équivalent.
     */
    public BigDecimal calculateAnnualEquivalent(Long amountMontant, OfferBillingPeriod period) {
        BigDecimal monthly = calculateMonthlyEquivalent(amountMontant, period);
        if (monthly == null) return null;
        return monthly.multiply(MONTHS_PER_YEAR).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le coût total d'une offre avec promotion sur un horizon.
     *
     * @param normalPrice le prix normal récurrent en centimes
     * @param period la périodicité
     * @param promotionPrice le prix promotionnel (peut être null)
     * @param promotionDurationMonths durée de la promotion en mois (peut être null)
     * @param horizonMonths l'horizon en mois
     * @return le coût total en centimes, ou null si impossible à calculer
     */
    public Long calculateTotalCostWithPromotion(
            Long normalPrice, OfferBillingPeriod period,
            Long promotionPrice, Integer promotionDurationMonths,
            Integer horizonMonths
    ) {
        if (normalPrice == null || normalPrice < 0 || horizonMonths == null || horizonMonths <= 0) {
            return null;
        }
        if (period == null || period == OfferBillingPeriod.UNKNOWN) {
            return null;
        }

        // Si pas de promotion, calcul simple
        if (promotionPrice == null || promotionDurationMonths == null || promotionDurationMonths <= 0) {
            return calculateTotalCost(normalPrice, period, horizonMonths);
        }

        // Nombre de mois au prix promotionnel (cappé à l'horizon)
        int promoMonths = Math.min(promotionDurationMonths, horizonMonths);
        int normalMonths = horizonMonths - promoMonths;

        Long promoCost = calculateTotalCost(promotionPrice, period, promoMonths);
        Long normalCost = calculateTotalCost(normalPrice, period, normalMonths);

        if (promoCost == null || normalCost == null) {
            return null;
        }

        // Vérification overflow
        try {
            return Math.addExact(promoCost, normalCost);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Calcule le coût mensuel effectif moyen sur l'horizon avec promotion.
     */
    public Long calculateEffectiveMonthlyCostWithPromotion(
            Long normalPrice, OfferBillingPeriod period,
            Long promotionPrice, Integer promotionDurationMonths,
            Integer horizonMonths
    ) {
        Long total = calculateTotalCostWithPromotion(normalPrice, period, promotionPrice, promotionDurationMonths, horizonMonths);
        if (total == null || horizonMonths == null || horizonMonths <= 0) {
            return null;
        }
        try {
            return Math.floorDiv(total, horizonMonths);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Vérifie qu'un montant en centimes ne dépasse pas Long.MAX_VALUE après multiplication.
     */
    public boolean isSafeAmount(Long amount) {
        return amount != null && amount >= 0 && amount < Long.MAX_VALUE / 1000;
    }
}
