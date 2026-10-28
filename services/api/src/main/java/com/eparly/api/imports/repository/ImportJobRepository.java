package com.eparly.api.imports.repository;

import com.eparly.api.imports.domain.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les opérations d'importation de relevés financiers.
 */
@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Optional<ImportJob> findByIdAndUserId(UUID id, UUID userId);

    List<ImportJob> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ImportJob> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);

    Optional<ImportJob> findByAccountIdAndFileHash(UUID accountId, String fileHash);

    boolean existsByAccountIdAndFileHash(UUID accountId, String fileHash);
}
