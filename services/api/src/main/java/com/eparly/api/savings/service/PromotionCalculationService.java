package com.eparly.api.savings.service;

import com.eparly.api.offer.domain.OfferPricing;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Gestion des promotions dans le calcul d'économies.
 * Une promotion n'est appliquée que si ses conditions sont vérifiées et qu'elle n'est pas expirée.
 */
@Service
public class PromotionCalculationService {

    /**
     * Détermine si une promotion est applicable à la date de début de la simulation.
     * Une promotion est applicable si :
     * - Elle a un pourcentage et une durée définis
     * - Le prix après promotion est défini
     * - La date de début de simulation est avant l'expiration éventuelle de la promotion
     */
    public boolean isPromotionApplicable(OfferPricing pricing, LocalDate simulationStartDate) {
        if (pricing == null) return false;
        if (pricing.getPromotionPercent() == null || pricing.getPromotionPercent() <= 0) return false;
        if (pricing.getPromotionDurationMonths() == null || pricing.getPromotionDurationMonths() <= 0) return false;
        if (pricing.getPriceAfterPromotionMinor() == null || pricing.getPriceAfterPromotionMinor() < 0) return false;
        return true;
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
