package com.eparly.api.offer.repository;

import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.ProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferProviderRepository extends JpaRepository<OfferProvider, UUID> {

    List<OfferProvider> findByStatus(ProviderStatus status);

    Optional<OfferProvider> findByIdAndStatus(UUID id, ProviderStatus status);

    @Query("select p from OfferProvider p where p.sector = :sector and p.status = :status order by p.name asc")
    List<OfferProvider> findBySectorAndStatus(
            @Param("sector") OfferSector sector,
            @Param("status") ProviderStatus status
    );

    boolean existsByNameAndSector(String name, OfferSector sector);
}
