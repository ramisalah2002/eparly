package com.eparly.api;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.category.domain.Category;
import com.eparly.api.category.domain.CategoryType;
import com.eparly.api.category.repository.CategoryRepository;
import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.repository.ImportJobRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie, par SQL direct (sans passer par les gardes applicatives), les contraintes de la migration V8.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class ImportIntegrityMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V8 : un ImportJob ne peut pas viser le compte d'un autre utilisateur")
    void importJobCannotTargetAnotherUsersAccount() {
        User owner = createUser();
        User intruder = createUser();
        Account account = accountRepository.saveAndFlush(new Account(owner, "Compte A"));

        assertThatThrownBy(() -> insertJob(intruder.getId(), account.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertJob(owner.getId(), account.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V8 : une transaction doit appartenir au meme compte que son ImportJob")
    void transactionAccountMustMatchImportJobAccount() {
        User user = createUser();
        Account accountA = accountRepository.saveAndFlush(new Account(user, "Compte A"));
        Account accountB = accountRepository.saveAndFlush(new Account(user, "Compte B"));
        ImportJob jobA = importJobRepository.saveAndFlush(new ImportJob(user, accountA, "hash-a"));

        assertThatThrownBy(() -> insertTransaction(accountB.getId(), jobA.getId(), "fp-1", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertTransaction(accountA.getId(), jobA.getId(), "fp-1", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V8 : une categorie privee d'un autre utilisateur est refusee, systeme et propre categorie acceptees")
    void privateCategoryOfAnotherUserIsRejected() {
        User owner = createUser();
        User other = createUser();
        Account account = accountRepository.saveAndFlush(new Account(owner, "Compte A"));
        ImportJob job = importJobRepository.saveAndFlush(new ImportJob(owner, account, "hash-cat"));

        Category foreign = categoryRepository.saveAndFlush(
                Category.createUserCategory(other, "Privee autre", CategoryType.EXPENSE, null));
        Category own = categoryRepository.saveAndFlush(
                Category.createUserCategory(owner, "Privee proprio", CategoryType.EXPENSE, null));
        Category system = categoryRepository.saveAndFlush(
                Category.createSystem("Systeme " + UUID.randomUUID(), CategoryType.EXPENSE, null));

        assertThatThrownBy(() -> insertTransaction(account.getId(), job.getId(), "fp-foreign", null, foreign.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertTransaction(account.getId(), job.getId(), "fp-own", null, own.getId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertTransaction(account.getId(), job.getId(), "fp-system", null, system.getId()))
                .doesNotThrowAnyException();

        // Mise a jour de la categorie vers une categorie etrangere : egalement refusee
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE transactions SET category_id = ? WHERE account_id = ? AND fingerprint = ?",
                foreign.getId(), account.getId(), "fp-own"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V8 : source_uid unique par compte, NULL multiples autorises, meme uid autorise sur un autre compte")
    void sourceUidIsUniquePerAccount() {
        User user = createUser();
        Account accountA = accountRepository.saveAndFlush(new Account(user, "Compte A"));
        Account accountB = accountRepository.saveAndFlush(new Account(user, "Compte B"));
        ImportJob jobA = importJobRepository.saveAndFlush(new ImportJob(user, accountA, "hash-uid-a"));
        ImportJob jobB = importJobRepository.saveAndFlush(new ImportJob(user, accountB, "hash-uid-b"));

        insertTransaction(accountA.getId(), jobA.getId(), "fp-u1", "BANK-REF-1", null);

        assertThatThrownBy(() -> insertTransaction(accountA.getId(), jobA.getId(), "fp-u2", "BANK-REF-1", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> {
            insertTransaction(accountA.getId(), jobA.getId(), "fp-n1", null, null);
            insertTransaction(accountA.getId(), jobA.getId(), "fp-n2", null, null);
            insertTransaction(accountB.getId(), jobB.getId(), "fp-u3", "BANK-REF-1", null);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V8 : les compteurs d'un ImportJob doivent rester coherents avec le total")
    void importJobCountersMustBeConsistent() {
        User user = createUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte A"));
        ImportJob job = importJobRepository.saveAndFlush(new ImportJob(user, account, "hash-sum"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE import_jobs SET total_rows = 5, inserted_rows = 4, skipped_rows = 2 WHERE id = ?", job.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE import_jobs SET total_rows = 5, inserted_rows = 3, skipped_rows = 2 WHERE id = ?", job.getId()))
                .doesNotThrowAnyException();
        Integer inserted = jdbcTemplate.queryForObject(
                "SELECT inserted_rows FROM import_jobs WHERE id = ?", Integer.class, job.getId());
        assertThat(inserted).isEqualTo(3);
    }

    private void insertJob(UUID userId, UUID accountId) {
        jdbcTemplate.update(
                "INSERT INTO import_jobs (user_id, account_id, file_hash, status) VALUES (?, ?, ?, 'PENDING')",
                userId, accountId, "h-" + UUID.randomUUID());
    }

    private void insertTransaction(UUID accountId, UUID jobId, String fingerprint, String sourceUid, UUID categoryId) {
        jdbcTemplate.update(
                "INSERT INTO transactions (account_id, import_job_id, source_uid, fingerprint, booking_date, "
                        + "amount_minor, currency, raw_label, normalized_label, category_id, transaction_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'EUR', 'LABEL', 'label', ?, 'UNKNOWN')",
                accountId, jobId, sourceUid, fingerprint, LocalDate.of(2025, 3, 4), -1000L, categoryId);
    }

    private User createUser() {
        User user = new User("v8-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
