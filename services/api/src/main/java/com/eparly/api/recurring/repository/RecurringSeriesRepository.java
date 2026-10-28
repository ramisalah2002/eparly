package com.eparly.api.recurring.repository;

import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringSeriesRepository extends JpaRepository<RecurringSeries, UUID> {

    @Query("select s from RecurringSeries s where s.id = :id and s.account.user.id = :userId")
    Optional<RecurringSeries> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select s from RecurringSeries s where s.account.user.id = :userId order by s.updatedAt desc")
    List<RecurringSeries> findAllByUserId(@Param("userId") UUID userId);

    @Query("select s from RecurringSeries s where s.account.user.id = :userId and (:accountId is null or s.account.id = :accountId) and (:status is null or s.status = :status) and (:type is null or s.seriesType = :type) order by s.updatedAt desc")
    List<RecurringSeries> findAllByUserIdFiltered(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("status") RecurringSeriesStatus status,
            @Param("type") RecurringSeriesType type
    );

    @Query("select s from RecurringSeries s where s.account.id = :accountId and s.normalizedLabel = :label and s.currency = :currency")
    Optional<RecurringSeries> findByAccountAndLabelAndCurrency(
            @Param("accountId") UUID accountId,
            @Param("label") String label,
            @Param("currency") String currency
    );

    boolean existsByAccountIdAndNormalizedLabelAndCurrency(UUID accountId, String normalizedLabel, String currency);

    @Query("select s from RecurringSeries s where s.account.id = :accountId")
    List<RecurringSeries> findAllByAccountId(@Param("accountId") UUID accountId);
}
