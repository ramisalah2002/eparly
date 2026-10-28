package com.eparly.api.subscription.repository;

import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("select s from Subscription s where s.id = :id and s.user.id = :userId")
    Optional<Subscription> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select s from Subscription s where s.user.id = :userId and (:accountId is null or s.account.id = :accountId) and (:status is null or s.declaredStatus = :status) order by s.updatedAt desc")
    List<Subscription> findAllByUserIdFiltered(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("status") SubscriptionStatus status
    );

    boolean existsByRecurringSeriesId(UUID recurringSeriesId);

    @Query("select s from Subscription s where s.recurringSeries.id = :seriesId")
    Optional<Subscription> findByRecurringSeriesId(@Param("seriesId") UUID seriesId);
}
