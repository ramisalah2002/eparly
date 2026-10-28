package com.eparly.api.subscription.repository;

import com.eparly.api.subscription.domain.SubscriptionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionStatusHistoryRepository extends JpaRepository<SubscriptionStatusHistory, UUID> {
    List<SubscriptionStatusHistory> findAllBySubscription_IdOrderByChangedAtAsc(UUID subscriptionId);
}
