package com.eparly.api.offer.dto;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Tarification d'une offre commerciale (montants en centimes)")
public record OfferPricingResponse(
        UUID id,
        Long amountMinor,
        String currency,
        OfferBillingPeriod billingPeriod,
        Long activationFeeMinor,
        String otherFees,
        Integer promotionPercent,
        Integer promotionDurationMonths,
        Long priceAfterPromotionMinor,
        String discountConditions,
        Boolean taxesIncluded,
        Boolean dataUnknown,
        Instant createdAt,
        Instant updatedAt
) {
}
