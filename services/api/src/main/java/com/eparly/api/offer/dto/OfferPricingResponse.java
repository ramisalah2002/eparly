package com.eparly.api.offer.dto;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.PromotionPriceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Tarification d'une offre commerciale (montants en centimes)")
public record OfferPricingResponse(
        UUID id,
        @Schema(description = "Prix récurrent standard, également appliqué après promotion, en unités mineures par cycle")
        Long amountMinor,
        String currency,
        OfferBillingPeriod billingPeriod,
        Long activationFeeMinor,
        String otherFees,
        @Schema(description = "Pourcentage temporaire uniquement si promotionPriceType=PERCENTAGE")
        Integer promotionPercent,
        Integer promotionDurationMonths,
        @Schema(description = "Prix temporaire fixe par cycle uniquement si promotionPriceType=FIXED_PRICE")
        Long priceAfterPromotionMinor,
        String discountConditions,
        Boolean taxesIncluded,
        Boolean dataUnknown,
        Instant createdAt,
        Instant updatedAt,
        PromotionPriceType promotionPriceType,
        LocalDate promotionValidFrom,
        LocalDate promotionValidUntil,
        @Schema(description = "Promotion applicable aujourd'hui avec preuve dédiée et fraîche")
        boolean promotionVerified,
        Long version
) {
}
