package com.eparly.api.offer.admin.dto;

import com.eparly.api.offer.domain.OfferReuseRights;

import java.util.UUID;

public record OfferReuseRightsResponse(UUID offerId, OfferReuseRights status, String basis, Long version) {
}
