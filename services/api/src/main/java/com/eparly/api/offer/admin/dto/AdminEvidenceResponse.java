package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferEvidenceScope;

import java.time.Instant;
import java.util.UUID;

public record AdminEvidenceResponse(
        UUID id,
        String officialUrl,
        Instant consultedAt,
        Instant verifiedAt,
        OfferEvidenceScope evidenceScope,
        String nature,
        String verifiedSummary,
        Instant expiresAt,
        EvidenceStatus verificationStatus,
        String provenance,
        Instant createdAt,
        Instant updatedAt,
        UUID verifiedByUserId,
        Long verifiedPricingVersion,
        Long verifiedProviderVersion,
        Long version
) {
}
