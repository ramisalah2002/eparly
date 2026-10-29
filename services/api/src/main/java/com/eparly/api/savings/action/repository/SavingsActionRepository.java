package com.eparly.api.savings.action.repository;

import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.SavingsAction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavingsActionRepository extends JpaRepository<SavingsAction, UUID>, JpaSpecificationExecutor<SavingsAction> {

    Optional<SavingsAction> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SavingsAction a where a.id = :id and a.user.id = :userId")
    Optional<SavingsAction> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    @Query("select a from SavingsAction a where a.user.id = :userId and (:status is null or a.status = :status) order by a.createdAt desc")
    Page<SavingsAction> findAllByUserIdFiltered(
            @Param("userId") UUID userId,
            @Param("status") ActionStatus status,
            Pageable pageable
    );

    @Query("select a from SavingsAction a where a.user.id = :userId and a.subscription.id = :subscriptionId and (:status is null or a.status = :status) order by a.createdAt desc")
    Page<SavingsAction> findAllByUserIdAndSubscriptionId(
            @Param("userId") UUID userId,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("status") ActionStatus status,
            Pageable pageable
    );

    @Query("select count(a) from SavingsAction a where a.user.id = :userId")
    long countByUserId(UUID userId);

    @Query("select count(a) from SavingsAction a where a.user.id = :userId and a.status = :status")
    long countByUserIdAndStatus(UUID userId, ActionStatus status);

    @Query("""
            select distinct a from SavingsAction a
            join fetch a.subscription s
            left join fetch s.account
            join fetch a.alternativeOffer o
            join fetch o.provider
            where a.user.id = :userId
              and (:accountId is null or s.account.id = :accountId)
            order by a.createdAt desc
            """)
    List<SavingsAction> findAllForMonthlyReport(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId
    );

    @Query("select a from SavingsAction a where a.subscription.id = :subscriptionId and a.user.id = :userId and a.status in ('PLANNED', 'COMMITTED', 'DECLARED')")
    List<SavingsAction> findActiveActionsBySubscriptionAndUserId(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("userId") UUID userId
    );

    @Query("select a from SavingsAction a where a.subscription.id = :subscriptionId and a.alternativeOffer.id = :offerId and a.status in ('PLANNED', 'COMMITTED', 'DECLARED')")
    List<SavingsAction> findActiveActionsBySubscriptionAndOffer(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("offerId") UUID offerId
    );
}
