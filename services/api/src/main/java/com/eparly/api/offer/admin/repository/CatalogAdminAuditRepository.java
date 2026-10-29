package com.eparly.api.offer.admin.repository;

import com.eparly.api.offer.admin.domain.CatalogAdminAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogAdminAuditRepository extends JpaRepository<CatalogAdminAudit, UUID> {

    List<CatalogAdminAudit> findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
            String resourceType, UUID resourceId);
}
