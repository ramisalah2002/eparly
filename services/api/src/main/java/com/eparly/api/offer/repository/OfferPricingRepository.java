package com.eparly.api.offer.repository;

import com.eparly.api.offer.domain.OfferPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferPricingRepository extends JpaRepository<OfferPricing, UUID> {

    Optional<OfferPricing> findByOfferId(UUID offerId);
}
