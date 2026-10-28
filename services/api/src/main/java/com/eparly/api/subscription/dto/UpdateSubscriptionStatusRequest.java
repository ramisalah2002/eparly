package com.eparly.api.subscription.dto;

import com.eparly.api.subscription.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSubscriptionStatusRequest(
        @NotNull SubscriptionStatus status,
        String note
) {
}
