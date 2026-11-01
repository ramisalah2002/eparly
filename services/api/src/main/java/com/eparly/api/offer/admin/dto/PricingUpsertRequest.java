package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.PromotionPriceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PricingUpsertRequest(
        Long expectedVersion,
        @PositiveOrZero Long amountMinor,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull OfferBillingPeriod billingPeriod,
        @PositiveOrZero Long activationFeeMinor,
        @Size(max = 2000) String otherFees,
        PromotionPriceType promotionPriceType,
        Integer promotionPercent,
        @Positive @Max(Integer.MAX_VALUE) Long promotionDurationMonths,
        @PositiveOrZero Long priceAfterPromotionMinor,
        LocalDate promotionValidFrom,
        LocalDate promotionValidUntil,
        @Size(max = 500) String discountConditions,
        @NotNull Boolean taxesIncluded,
        @NotNull Boolean dataUnknown,
        @Size(max = 500) String reason
) {
}
