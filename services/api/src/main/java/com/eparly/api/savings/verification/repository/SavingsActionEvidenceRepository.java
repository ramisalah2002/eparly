package com.eparly.api.savings.verification.repository;

import com.eparly.api.savings.verification.domain.EvidenceRole;
import com.eparly.api.savings.verification.domain.SavingsActionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavingsActionEvidenceRepository extends JpaRepository<SavingsActionEvidence, UUID> {

    @Query("select e from SavingsActionEvidence e where e.id = :id and e.user.id = :userId")
    Optional<SavingsActionEvidence> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select e from SavingsActionEvidence e where e.id = :id and e.action.id = :actionId and e.user.id = :userId")
    Optional<SavingsActionEvidence> findByIdAndActionIdAndUserId(@Param("id") UUID id, @Param("actionId") UUID actionId, @Param("userId") UUID userId);

    Optional<SavingsActionEvidence> findByActionIdAndTransactionId(UUID actionId, UUID transactionId);

    @Query("select e from SavingsActionEvidence e join fetch e.transaction where e.action.id = :actionId order by e.transaction.bookingDate asc")
    List<SavingsActionEvidence> findAllByActionIdOrderByTransactionBookingDateAsc(@Param("actionId") UUID actionId);

    List<SavingsActionEvidence> findAllByActionId(UUID actionId);

    List<SavingsActionEvidence> findAllByActionIdAndRole(UUID actionId, EvidenceRole role);

    @Query("""
            select e from SavingsActionEvidence e
            join fetch e.transaction t
            where e.user.id = :userId
              and e.action.id in :actionIds
            order by t.bookingDate asc
            """)
    List<SavingsActionEvidence> findAllForMonthlyReport(
            @Param("userId") UUID userId,
            @Param("actionIds") List<UUID> actionIds
    );

    boolean existsByTransactionId(UUID transactionId);

    boolean existsByActionIdAndTransactionId(UUID actionId, UUID transactionId);

    long countByActionId(UUID actionId);
}
