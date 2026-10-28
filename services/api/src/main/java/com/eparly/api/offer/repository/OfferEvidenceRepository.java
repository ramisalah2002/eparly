package com.eparly.api.offer.repository;

import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferEvidenceRepository extends JpaRepository<OfferEvidence, UUID> {

    Optional<OfferEvidence> findByOfferIdAndVerificationStatus(UUID offerId, EvidenceStatus status);

    List<OfferEvidence> findAllByOfferIdOrderByVerifiedAtDesc(UUID offerId);

    @Query("select e from OfferEvidence e where e.verificationStatus = :status and e.verifiedAt < :before")
    List<OfferEvidence> findExpiredEvidence(
            @Param("status") EvidenceStatus status,
            @Param("before") Instant before
    );
}
