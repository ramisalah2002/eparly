package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.ProviderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProviderPatchRequest(
        @NotNull Long expectedVersion,
        @Size(min = 1, max = 200) String name,
        OfferSector sector,
        @Pattern(regexp = "(?i)[a-z]{2}") String country,
        @Pattern(regexp = "(?i)^https://[^\\s]+$") @Size(max = 500) String officialWebsite,
        ProviderStatus status
) {
}
