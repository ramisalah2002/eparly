package com.eparly.api.savings.verification.repository;

import com.eparly.api.savings.verification.domain.SavingsActionVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavingsActionVerificationRepository extends JpaRepository<SavingsActionVerification, UUID> {

    @Query("select v from SavingsActionVerification v where v.action.id = :actionId order by v.evaluatedAt desc limit 1")
    Optional<SavingsActionVerification> findLatestByActionId(@Param("actionId") UUID actionId);

    List<SavingsActionVerification> findAllByActionIdOrderByEvaluatedAtDesc(UUID actionId);

    @Query("""
            select v from SavingsActionVerification v
            join fetch v.action a
            where v.user.id = :userId
              and a.id in :actionIds
            order by v.evaluatedAt desc
            """)
    List<SavingsActionVerification> findAllForMonthlyReport(
            @Param("userId") UUID userId,
            @Param("actionIds") List<UUID> actionIds
    );

    long countByActionId(UUID actionId);
}
