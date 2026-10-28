package com.eparly.api;

import com.eparly.api.account.domain.Account;
import com.eparly.api.account.domain.AccountSourceType;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.category.domain.Category;
import com.eparly.api.category.domain.CategoryType;
import com.eparly.api.category.repository.CategoryRepository;
import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.domain.ImportJobStatus;
import com.eparly.api.imports.repository.ImportJobRepository;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.domain.TransactionType;
import com.eparly.api.transaction.repository.TransactionRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class FinancialDomainIntegrationTest {

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
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // 1 & 2 : Création de comptes et association stricte à l'utilisateur
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1 & 2. Création de comptes financiers et cloisonnement strict par utilisateur")
    void accountCreationAndStrictUserIsolation() {
        User user1 = createTestUser();
        User user2 = createTestUser();

        Account account1 = new Account(user1, "Compte Courant Principal", AccountSourceType.CSV, "EUR");
        accountRepository.saveAndFlush(account1);

        Account account2 = new Account(user2, "Livret A", AccountSourceType.CSV, "EUR");
        accountRepository.saveAndFlush(account2);

        // L'utilisateur 1 accède uniquement à son compte
        List<Account> user1Accounts = accountRepository.findAllByUserId(user1.getId());
        assertThat(user1Accounts).hasSize(1);
        assertThat(user1Accounts.get(0).getName()).isEqualTo("Compte Courant Principal");

        // L'accès transversal par ID et mauvais utilisateur est impossible
        Optional<Account> crossAccess = accountRepository.findByIdAndUserId(account1.getId(), user2.getId());
        assertThat(crossAccess).isEmpty();

        // Unicité du nom de compte par utilisateur
        Account duplicateName = new Account(user1, "Compte Courant Principal", AccountSourceType.CSV, "EUR");
        assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicateName))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------------------
    // 3 : Création de catégories (système, utilisateur et hiérarchie)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. Création de catégories système et personnalisées avec organisation hiérarchique")
    void categoryCreationAndHierarchy() {
        User user = createTestUser();

        // Catégorie système racine
        Category systemRoot = Category.createSystem("Alimentation", CategoryType.EXPENSE, null);
        categoryRepository.saveAndFlush(systemRoot);

        // Sous-catégorie système
        Category systemSub = Category.createSystem("Supermarché", CategoryType.EXPENSE, systemRoot);
        categoryRepository.saveAndFlush(systemSub);

        // Catégorie personnalisée de l'utilisateur
        Category customCategory = Category.createUserCategory(user, "Projets Personnels", CategoryType.EXPENSE, null);
        categoryRepository.saveAndFlush(customCategory);

        // Vérification de la visibilité des catégories pour l'utilisateur
        List<Category> visibleCategories = categoryRepository.findVisibleCategoriesForUser(user.getId());
        assertThat(visibleCategories).extracting(Category::getName)
                .contains("Alimentation", "Supermarché", "Projets Personnels");

        assertThat(systemSub.getParent()).isNotNull();
        assertThat(systemSub.getParent().getName()).isEqualTo("Alimentation");
        assertThat(systemSub.isSystem()).isTrue();
        assertThat(customCategory.isSystem()).isFalse();
        assertThat(customCategory.getUser().getId()).isEqualTo(user.getId());
    }

    // -------------------------------------------------------------------------
    // 4 & 5 : Enregistrement des transactions et stockage exact en centimes (BIGINT)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4 & 5. Enregistrement des transactions et stockage exact en centimes (BIGINT)")
    void transactionCreationAndCentsExactPrecision() {
        User user = createTestUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte Courant"));
        ImportJob importJob = importJobRepository.saveAndFlush(new ImportJob(user, account, "hash-csv-001"));

        // Dépense de 45.99 € => stockée exactement sous forme de -4599 centimes
        Long amountCents = -4599L;
        Transaction tx = new Transaction(
                account,
                importJob,
                "fingerprint-tx-001",
                LocalDate.of(2026, 10, 28),
                amountCents,
                "CB MONOPRIX PARIS 15",
                "Monoprix",
                TransactionType.EXPENSE
        );
        tx.setValueDate(LocalDate.of(2026, 10, 29));
        transactionRepository.saveAndFlush(tx);

        // Vérification Java
        Transaction retrieved = transactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(retrieved.getAmountMinor()).isEqualTo(-4599L);
        assertThat(retrieved.isExpense()).isTrue();
        assertThat(retrieved.isTransfer()).isFalse();

        // Vérification directe PostgreSQL (type colonne BIGINT)
        Long dbAmount = jdbcTemplate.queryForObject(
                "SELECT amount_minor FROM transactions WHERE id = ?",
                Long.class,
                tx.getId()
        );
        assertThat(dbAmount).isEqualTo(-4599L);
    }

    // -------------------------------------------------------------------------
    // 6 : Respect des contraintes NOT NULL
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6. Respect des contraintes NOT NULL sur les transactions")
    void notNullConstraintsEnforced() {
        User user = createTestUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte"));
        ImportJob importJob = importJobRepository.saveAndFlush(new ImportJob(user, account, "hash-csv-002"));

        // Transaction avec montant null invalide
        Transaction invalidTx = new Transaction(
                account,
                importJob,
                "fingerprint-missing-amount",
                LocalDate.now(),
                null, // amount_minor NOT NULL violé
                "TEST LABEL",
                "TEST LABEL",
                TransactionType.EXPENSE
        );

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(invalidTx))
                .isInstanceOf(Exception.class);
    }

    // -------------------------------------------------------------------------
    // 7 : Intégrité des clés étrangères et protection ON DELETE RESTRICT
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7. Intégrité des clés étrangères et protection ON DELETE RESTRICT contre les suppressions en cascade")
    void foreignKeyIntegrityAndRestrictRules() {
        User user = createTestUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte Protégé"));
        ImportJob importJob = importJobRepository.saveAndFlush(new ImportJob(user, account, "hash-003"));
        Transaction tx = transactionRepository.saveAndFlush(new Transaction(
                account, importJob, "fp-fk-test", LocalDate.now(), 1000L, "DEPOT", "Depot", TransactionType.INCOME
        ));

        // Interdiction de supprimer le compte tant que des transactions y sont rattachées
        assertThatThrownBy(() -> {
            accountRepository.delete(account);
            accountRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Interdiction de supprimer l'import job référencé par une transaction
        assertThatThrownBy(() -> {
            importJobRepository.delete(importJob);
            importJobRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Interdiction de supprimer l'utilisateur propriétaire du compte
        assertThatThrownBy(() -> {
            userRepository.delete(user);
            userRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------------------
    // 8 : Historique et métriques des opérations d'import
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("8. Historique et métriques des opérations d'import (ImportJob)")
    void importJobLifecycleAndMetricsTracking() {
        User user = createTestUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte Import"));

        ImportJob job = new ImportJob(user, account, "sha256-file-content-abcdef123456");
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.PENDING);
        importJobRepository.saveAndFlush(job);

        // Mise à jour métriques et complétion
        job.markCompleted(150, 145, 5, 0);
        importJobRepository.saveAndFlush(job);

        ImportJob completedJob = importJobRepository.findById(job.getId()).orElseThrow();
        assertThat(completedJob.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
        assertThat(completedJob.getTotalRows()).isEqualTo(150);
        assertThat(completedJob.getInsertedRows()).isEqualTo(145);
        assertThat(completedJob.getSkippedRows()).isEqualTo(5);
        assertThat(completedJob.getFailedRows()).isEqualTo(0);
        assertThat(completedJob.getCompletedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // 9 : Contraintes de déduplication et préservation des opérations réelles distinctes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("9. Contraintes de déduplication : rejet des doublons réels et préservation de transactions identiques légitimes")
    void deduplicationConstraintsAndPreservationOfLegitimateDuplicates() {
        User user = createTestUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte Dedup"));
        ImportJob importJob = importJobRepository.saveAndFlush(new ImportJob(user, account, "hash-dedup"));

        LocalDate date = LocalDate.of(2026, 10, 28);
        Long amount = -250L; // 2.50 €
        String label = "CAFE DE LA GARE";

        // Cas A : Deux cafés légitimes achetés le même jour (occurrences distinctes dans le relevé)
        String fpCoffee1 = "fp-cafe-01"; // hash incluant sequence/ligne 1
        String fpCoffee2 = "fp-cafe-02"; // hash incluant sequence/ligne 2

        Transaction coffee1 = new Transaction(account, importJob, fpCoffee1, date, amount, label, "Café", TransactionType.EXPENSE);
        Transaction coffee2 = new Transaction(account, importJob, fpCoffee2, date, amount, label, "Café", TransactionType.EXPENSE);

        transactionRepository.saveAndFlush(coffee1);
        transactionRepository.saveAndFlush(coffee2);

        // Les deux opérations légitimes coexistent parfaitement
        List<Transaction> transactions = transactionRepository.findAllByAccountIdOrderByBookingDateDesc(account.getId());
        assertThat(transactions).hasSize(2);

        // Cas B : Tentative de ré-import d'une transaction avec le même fingerprint sur le même compte
        Transaction duplicateCoffee = new Transaction(account, importJob, fpCoffee1, date, amount, label, "Café", TransactionType.EXPENSE);
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(duplicateCoffee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------------------
    // Prévalence des catégories utilisateur sur les automatisations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("La classification manuelle par l'utilisateur prévaut sur les règles automatiques")
    void userCustomCategorizationPrevalence() {
        User user = createTestUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte Cat"));
        ImportJob importJob = importJobRepository.saveAndFlush(new ImportJob(user, account, "hash-cat"));

        Category autoCat = categoryRepository.saveAndFlush(Category.createSystem("Divers", CategoryType.EXPENSE, null));
        Category manualCat = categoryRepository.saveAndFlush(Category.createUserCategory(user, "Cadeaux", CategoryType.EXPENSE, null));

        Transaction tx = new Transaction(account, importJob, "fp-cat-test", LocalDate.now(), -1500L, "AMAZON", "Amazon", TransactionType.EXPENSE);
        tx.setCategory(autoCat);
        assertThat(tx.isCustomCategoryFlag()).isFalse();
        transactionRepository.saveAndFlush(tx);

        // L'utilisateur change manuellement la catégorie
        tx.assignUserCategory(manualCat);
        transactionRepository.saveAndFlush(tx);

        Transaction updated = transactionRepository.findWithCategoryById(tx.getId()).orElseThrow();
        assertThat(updated.isCustomCategoryFlag()).isTrue();
        assertThat(updated.getCategory().getName()).isEqualTo("Cadeaux");
    }

    // -------------------------------------------------------------------------
    // Méthodes utilitaires
    // -------------------------------------------------------------------------

    private User createTestUser() {
        User user = new User("fin-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
