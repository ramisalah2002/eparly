package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.OfferReuseRights;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfferReuseRightsRequest(
        @NotNull Long expectedVersion,
        @NotNull OfferReuseRights status,
        @NotBlank @Size(max = 500) String basis
) {
}
