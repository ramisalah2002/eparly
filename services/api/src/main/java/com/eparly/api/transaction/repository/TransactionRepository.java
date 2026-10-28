package com.eparly.api.transaction.repository;

import com.eparly.api.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les transactions financières.
 * Toutes les requêtes d'accès sont cloisonnées par compte et utilisateur propriétaire.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("select t from Transaction t where t.id = :id and t.account.user.id = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select t from Transaction t left join fetch t.category where t.id = :id")
    Optional<Transaction> findWithCategoryById(@Param("id") UUID id);

    List<Transaction> findAllByAccountIdOrderByBookingDateDesc(UUID accountId);

    @Query("select t from Transaction t where t.account.user.id = :userId order by t.bookingDate desc")
    List<Transaction> findAllByUserIdOrderByBookingDateDesc(@Param("userId") UUID userId);

    @Query("select t from Transaction t where t.account.id = :accountId and t.bookingDate between :startDate and :endDate order by t.bookingDate desc")
    List<Transaction> findAllByAccountIdAndBookingDateBetween(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Optional<Transaction> findByAccountIdAndFingerprint(UUID accountId, String fingerprint);

    boolean existsByAccountIdAndFingerprint(UUID accountId, String fingerprint);

    /** Empreintes deja presentes dans un compte (lecture seule, a appeler apres controle de propriete du compte). */
    @Query("select t.fingerprint from Transaction t where t.account.id = :accountId and t.fingerprint in :fingerprints")
    List<String> findExistingFingerprints(
            @Param("accountId") UUID accountId,
            @Param("fingerprints") Collection<String> fingerprints
    );

    List<Transaction> findAllByImportJobId(UUID importJobId);
}
