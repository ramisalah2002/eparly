package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.EvidenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvidenceVerificationRequest(
        @NotNull Long expectedVersion,
        @NotNull EvidenceStatus status,
        @NotBlank @Size(max = 2000) String summary
) {
}
