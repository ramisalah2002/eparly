package com.eparly.api.savings.service;

import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.PromotionPriceType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Gestion des promotions dans le calcul d'économies.
 * Une promotion n'est appliquée que si son modèle, son éligibilité et sa preuve sont vérifiés.
 */
@Service
public class PromotionCalculationService {

    /**
     * Détermine si une promotion est applicable à la date de début de la simulation.
     * Une promotion est applicable si :
     * - Le mode tarifaire et la durée sont complets et mutuellement exclusifs
     * - Une preuve promotionnelle dédiée et vérifiée est transmise
     * - La date de simulation se trouve dans la fenêtre de souscription
     */
    public boolean isPromotionApplicable(OfferPricing pricing, CommercialOffer offer, LocalDate simulationStartDate,
                                         OfferEvidence promotionEvidence) {
        if (pricing == null || offer == null) return false;
        if (simulationStartDate == null || promotionEvidence == null
                || promotionEvidence.getEvidenceScope() != OfferEvidenceScope.PROMOTION
                || promotionEvidence.getVerificationStatus() != EvidenceStatus.VERIFIED
                || promotionEvidence.getVerifiedAt() == null) return false;
        if (pricing.getAmountMinor() == null || pricing.getAmountMinor() < 0) return false;
        if (pricing.getPromotionDurationMonths() == null || pricing.getPromotionDurationMonths() <= 0) return false;
        if (pricing.getDiscountConditions() != null && !pricing.getDiscountConditions().isBlank()) return false;
        if (offer.getEligibilityConditions() != null && !offer.getEligibilityConditions().isBlank()) return false;
        if (pricing.getPromotionValidFrom() != null
                && simulationStartDate.isBefore(pricing.getPromotionValidFrom())) return false;
        if (pricing.getPromotionValidUntil() != null
                && simulationStartDate.isAfter(pricing.getPromotionValidUntil())) return false;

        if (pricing.getPromotionPriceType() == null) return false;
        return switch (pricing.getPromotionPriceType()) {
            case PERCENTAGE -> pricing.getPromotionPercent() != null
                    && pricing.getPromotionPercent() > 0
                    && pricing.getPromotionPercent() <= 100
                    && pricing.getPriceAfterPromotionMinor() == null;
            case FIXED_PRICE -> pricing.getPromotionPercent() == null
                    && pricing.getPriceAfterPromotionMinor() != null
                    && pricing.getPriceAfterPromotionMinor() >= 0
                    && pricing.getPriceAfterPromotionMinor() <= pricing.getAmountMinor();
        };
    }

    public Long promotionPriceMinor(OfferPricing pricing) {
        if (pricing == null || pricing.getAmountMinor() == null) return null;
        PromotionPriceType type = pricing.getPromotionPriceType();
        if (type == PromotionPriceType.PERCENTAGE) {
            if (pricing.getPromotionPercent() == null
                    || pricing.getPromotionPercent() <= 0
                    || pricing.getPromotionPercent() > 100) return null;
            return BigDecimal.valueOf(pricing.getAmountMinor())
                    .multiply(BigDecimal.valueOf(100L - pricing.getPromotionPercent()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
        if (type == PromotionPriceType.FIXED_PRICE) {
            Long fixedPrice = pricing.getPriceAfterPromotionMinor();
            return fixedPrice != null && fixedPrice >= 0
                    && fixedPrice <= pricing.getAmountMinor() ? fixedPrice : null;
        }
        return null;
    }

    public boolean hasPromotion(OfferPricing pricing) {
        return pricing != null && (pricing.getPromotionPercent() != null
                || pricing.getPriceAfterPromotionMinor() != null
                || pricing.getPromotionPriceType() != null
                || pricing.getPromotionDurationMonths() != null
                || pricing.getPromotionValidFrom() != null
                || pricing.getPromotionValidUntil() != null
                || (pricing.getDiscountConditions() != null
                && !pricing.getDiscountConditions().isBlank()));
    }

    /**
     * Calcule la date de fin de la promotion à partir de la date de début de simulation.
     */
    public LocalDate calculatePromotionEndDate(LocalDate simulationStartDate, Integer promotionDurationMonths) {
        if (simulationStartDate == null || promotionDurationMonths == null) {
            return null;
        }
        return simulationStartDate.plusMonths(promotionDurationMonths);
    }

    /**
     * Vérifie si une promotion est encore valide à une date donnée.
     */
    public boolean isPromotionValidAt(LocalDate promotionStartDate, Integer promotionDurationMonths, LocalDate checkDate) {
        if (promotionStartDate == null || promotionDurationMonths == null || checkDate == null) {
            return false;
        }
        LocalDate endDate = promotionStartDate.plusMonths(promotionDurationMonths);
        return !checkDate.isAfter(endDate);
    }
}
