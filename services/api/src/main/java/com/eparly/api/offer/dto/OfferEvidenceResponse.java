package com.eparly.api.offer.dto;

import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Preuve / source officielle verifiee d'une offre commerciale")
public record OfferEvidenceResponse(
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
        Instant updatedAt
) {
}
