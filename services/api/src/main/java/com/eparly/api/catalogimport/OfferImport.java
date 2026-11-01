package com.eparly.api.catalogimport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record OfferImport(
        String providerKey,
        String commercialName,
        String sector,
        String territory,
        Map<String, Object> characteristics,
        String eligibilityConditions,
        String commitment,
        LocalDate validFrom,
        LocalDate validUntil,
        PricingImport pricing,
        EvidenceImport evidence
) {
    public record PricingImport(
            Long amountMinor,
            String currency,
            String billingPeriod,
            Long activationFeeMinor,
            String otherFees,
            String promotionPriceType,
            Integer promotionPercent,
            Long promotionDurationMonths,
            Long priceAfterPromotionMinor,
            LocalDate promotionValidFrom,
            LocalDate promotionValidUntil,
            String discountConditions,
            Boolean taxesIncluded,
            Boolean dataUnknown,
            String reason
    ) {}

    public record EvidenceImport(
            String officialUrl,
            Instant consultedAt,
            Instant expiresAt,
            String nature,
            String provenance,
            String evidenceScope
    ) {}
}