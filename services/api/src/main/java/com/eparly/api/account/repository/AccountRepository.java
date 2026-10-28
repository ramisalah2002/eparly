package com.eparly.api.account.repository;

import com.eparly.api.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les comptes financiers.
 * Toutes les méthodes d'accès respectent la frontière du propriétaire utilisateur.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findAllByUserId(UUID userId);

    List<Account> findAllByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);
}
