package com.eparly.api.offer.repository;

import com.eparly.api.offer.domain.OfferPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OfferPriceHistoryRepository extends JpaRepository<OfferPriceHistory, UUID> {

    List<OfferPriceHistory> findAllByOfferIdOrderByChangedAtDesc(UUID offerId);
}
