package com.eparly.api.offer.repository;

import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommercialOfferRepository extends JpaRepository<CommercialOffer, UUID>, JpaSpecificationExecutor<CommercialOffer> {

    Optional<CommercialOffer> findByIdAndPublicationStatus(UUID id, OfferStatus publicationStatus);

    @Query("select o from CommercialOffer o where o.publicationStatus = :status order by o.createdAt desc")
    List<CommercialOffer> findByPublicationStatus(@Param("status") OfferStatus status);

    @Query("select o from CommercialOffer o where o.provider.id = :providerId order by o.createdAt desc")
    List<CommercialOffer> findByProviderId(@Param("providerId") UUID providerId);

    boolean existsByCommercialNameAndProviderId(String commercialName, UUID providerId);
}
