package com.eparly.api.imports.service;

import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.category.classification.CategoryClassificationService;
import com.eparly.api.category.domain.Category;
import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.domain.ImportJobStatus;
import com.eparly.api.imports.dto.ImportJobResponse;
import com.eparly.api.imports.repository.ImportJobRepository;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance atomique d'un import valide, dans une seule transaction :
 * <ol>
 *   <li>verrou advisory transactionnel par compte (serialise les imports concurrents d'un meme compte) ;</li>
 *   <li>creation de l'ImportJob (utilisateur et compte issus de l'entite autorisee, verifies par V8) ;</li>
 *   <li>insertion native {@code ON CONFLICT DO NOTHING} (couvre uq_transactions_account_fingerprint et l'index
 *       partiel sur source_uid) : le nombre de lignes affectees donne les insertions, le reste est ignore ;</li>
 *   <li>catégorisation automatique des nouvelles transactions par le moteur deterministe ;</li>
 *   <li>cloture du job avec des compteurs exacts.</li>
 * </ol>
 * Aucun libelle ni montant n'est journalise.
 */
@Service
public class ImportPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ImportPersistenceService.class);
    private static final int INSERT_BATCH_SIZE = 500;
    private static final String INSERT_PREFIX = "insert into transactions (account_id, import_job_id, source_uid, "
            + "fingerprint, booking_date, value_date, amount_minor, currency, raw_label, normalized_label, "
            + "transaction_type) values ";
    private static final String ROW_PLACEHOLDERS = "(?,?,?,?,?,?,?,?,?,?,?)";

    private final ImportJobRepository importJobRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ImportProperties properties;
    private final CategoryClassificationService classificationService;

    public ImportPersistenceService(
            ImportJobRepository importJobRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            JdbcTemplate jdbcTemplate,
            ImportProperties properties,
            CategoryClassificationService classificationService
    ) {
        this.importJobRepository = importJobRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.classificationService = classificationService;
    }

    @Transactional
    public ImportJobResponse persist(
            UUID userId,
            UUID accountId,
            String fileHash,
            int totalRows,
            List<ImportCandidate> candidates
    ) {
        acquireAccountLock(accountId);

        ImportJob job = new ImportJob(userRepository.getReferenceById(userId),
                accountRepository.getReferenceById(accountId), fileHash);
        job.setStatus(ImportJobStatus.PROCESSING);
        job.setTotalRows(totalRows);
        importJobRepository.saveAndFlush(job);

        int inserted = 0;
        for (int from = 0; from < candidates.size(); from += INSERT_BATCH_SIZE) {
            inserted += insertBatch(accountId, job.getId(),
                    candidates.subList(from, Math.min(from + INSERT_BATCH_SIZE, candidates.size())));
        }
        int skipped = candidates.size() - inserted;

        // Catégorisation automatique des nouvelles transactions
        classifyNewTransactions(job.getId(), candidates);

        job.markCompleted(totalRows, inserted, skipped, 0);
        importJobRepository.saveAndFlush(job);
        log.info("Import completed: job={}, total={}, inserted={}, skipped={}", job.getId(), totalRows, inserted, skipped);
        return ImportJobResponse.from(job);
    }

    /**
     * Applique la catégorisation automatique uniquement aux transactions nouvellement insérées.
     * Ne modifie jamais customCategoryFlag, montant, date, compte ou fingerprint.
     */
    private void classifyNewTransactions(UUID jobId, List<ImportCandidate> candidates) {
        for (ImportCandidate candidate : candidates) {
            Optional<Category> category = classificationService.classify(
                    candidate.rawLabel(),
                    candidate.normalizedLabel(),
                    candidate.rawCategory(),
                    candidate.rawSubcategory()
            );
            if (category.isPresent()) {
                // UPDATE uniquement si la transaction n'a pas déjà de catégorie
                // et si elle appartient bien à ce job (donc nouvellement insérée)
                jdbcTemplate.update(
                        "UPDATE transactions SET category_id = ? "
                                + "WHERE import_job_id = ? AND fingerprint = ? AND category_id IS NULL",
                        category.get().getId(), jobId, candidate.fingerprint());
            }
        }
    }

    private void acquireAccountLock(UUID accountId) {
        jdbcTemplate.execute("set local lock_timeout = " + properties.processingTimeout().toMillis());
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> {
                }, accountId.toString());
    }

    private int insertBatch(UUID accountId, UUID jobId, List<ImportCandidate> batch) {
        String sql = INSERT_PREFIX + String.join(",", Collections.nCopies(batch.size(), ROW_PLACEHOLDERS))
                + " on conflict do nothing";
        return jdbcTemplate.update(sql, statement -> {
            int index = 1;
            for (ImportCandidate candidate : batch) {
                statement.setObject(index++, accountId);
                statement.setObject(index++, jobId);
                if (candidate.sourceUid() == null) {
                    statement.setNull(index++, Types.VARCHAR);
                } else {
                    statement.setString(index++, candidate.sourceUid());
                }
                statement.setString(index++, candidate.fingerprint());
                statement.setObject(index++, candidate.bookingDate());
                if (candidate.valueDate() == null) {
                    statement.setNull(index++, Types.DATE);
                } else {
                    statement.setObject(index++, candidate.valueDate());
                }
                statement.setLong(index++, candidate.amountMinor());
                statement.setString(index++, candidate.currency());
                statement.setString(index++, candidate.rawLabel());
                statement.setString(index++, candidate.normalizedLabel());
                // Un montant positif n'est jamais deduit comme revenu : la qualification est repoussee a l'etape 7C.
                statement.setString(index++, candidate.amountMinor() < 0 ? "EXPENSE" : "UNKNOWN");
            }
        });
    }
}
