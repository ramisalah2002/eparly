package com.eparly.api.offer.repository;

import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OfferEvidenceRepository extends JpaRepository<OfferEvidence, UUID> {

    List<OfferEvidence> findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(UUID offerId);

    List<OfferEvidence> findAllByOfferIdAndEvidenceScopeOrderByConsultedAtDescCreatedAtDescIdDesc(
            UUID offerId, OfferEvidenceScope evidenceScope);

    boolean existsByOfferIdAndOfficialUrlAndEvidenceScopeAndConsultedAt(
            UUID offerId, String officialUrl, OfferEvidenceScope evidenceScope,
            java.time.Instant consultedAt);
}
