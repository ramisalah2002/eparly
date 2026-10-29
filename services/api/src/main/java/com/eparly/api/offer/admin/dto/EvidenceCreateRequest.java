package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.OfferEvidenceScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EvidenceCreateRequest(
        @NotBlank @Size(max = 1000) String officialUrl,
        @NotNull OfferEvidenceScope evidenceScope,
        @NotBlank @Size(max = 200) String nature,
        @NotBlank @Size(max = 200) String provenance,
        @NotNull Instant consultedAt,
        Instant expiresAt
) {
}
