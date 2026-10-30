package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.category.classification.CategoryClassificationService;
import com.eparly.api.category.domain.Category;
import com.eparly.api.category.domain.CategoryType;
import com.eparly.api.category.repository.CategoryRepository;
import com.eparly.api.category.service.CategoryReclassificationService;
import com.eparly.api.imports.ImportHttpClient;
import com.eparly.api.imports.ImportTestSupport;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.bearer;
import static com.eparly.api.imports.ImportTestSupport.createAccount;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class CategoryClassificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CategoryClassificationService classificationService;
    @Autowired
    private CategoryReclassificationService reclassificationService;
    @MockitoBean
    private JavaMailSender mailSender;

    // -------------------------------------------------------------------------
    // 1. Catégories système initialisées par V9
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Les 12 catégories système sont présentes après la migration V9")
    void systemCategoriesAreInitialized() {
        List<Category> systemCategories = categoryRepository.findAllBySystemFlagTrue();
        assertThat(systemCategories).hasSize(12);

        Set<String> names = systemCategories.stream().map(Category::getName).collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder(
                "Abonnements", "Autres dépenses", "Courses", "Frais bancaires",
                "Loisirs", "Logement", "Revenus", "Restaurants", "Santé",
                "Shopping", "Transport", "Virements"
        );

        // Vérifier que les catégories système n'ont pas de propriétaire
        assertThat(systemCategories).allSatisfy(cat -> {
            assertThat(cat.isSystemFlag()).isTrue();
            assertThat(cat.getUser()).isNull();
        });
    }

    @Test
    @DisplayName("Les types de catégorie système sont cohérents")
    void systemCategoryTypesAreCorrect() {
        Category revenus = findSystemCategory("Revenus");
        Category courses = findSystemCategory("Courses");
        Category virements = findSystemCategory("Virements");

        assertThat(revenus.getCategoryType()).isEqualTo(CategoryType.INCOME);
        assertThat(courses.getCategoryType()).isEqualTo(CategoryType.EXPENSE);
        assertThat(virements.getCategoryType()).isEqualTo(CategoryType.BOTH);
    }

    // -------------------------------------------------------------------------
    // 2. Moteur de classification – règles connues
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Classification : supermarchés sont catégorisés Courses")
    void classifiesSupermarketsAsCourses() {
        assertThat(classificationService.classify("CARREFOUR PARIS 15E")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Courses");
        assertThat(classificationService.classify("LECLERC NANTES")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Courses");
        assertThat(classificationService.classify("AUCHAN MARKET BORDEAUX")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Courses");
        assertThat(classificationService.classify("MONOPRIX LYON")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Courses");
    }

    @Test
    @DisplayName("Classification : fast-food et livraison sont catégorisés Restaurants")
    void classifiesRestaurants() {
        assertThat(classificationService.classify("MCDONALD PARIS")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Restaurants");
        assertThat(classificationService.classify("KFC LYON")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Restaurants");
        assertThat(classificationService.classify("DELIVEROO PARIS")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Restaurants");
        assertThat(classificationService.classify("UBER EATS LYON")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Restaurants");
    }

    @Test
    @DisplayName("Classification : transport et carburant")
    void classifiesTransport() {
        assertThat(classificationService.classify("SNCF OUIGO PARIS LYON")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Transport");
        assertThat(classificationService.classify("TOTAL ENERGIES PARIS")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Transport");
        assertThat(classificationService.classify("SHELL NICE")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Transport");
        assertThat(classificationService.classify("RATP METRO")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Transport");
    }

    @Test
    @DisplayName("Classification : abonnements")
    void classifiesSubscriptions() {
        assertThat(classificationService.classify("NETFLIX COM")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Abonnements");
        assertThat(classificationService.classify("SPOTIFY ABONNEMENT")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Abonnements");
        assertThat(classificationService.classify("AMAZON PRIME")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Abonnements");
    }

    @Test
    @DisplayName("Classification : shopping en ligne")
    void classifiesShopping() {
        assertThat(classificationService.classify("AMAZON FR SAS")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Shopping");
        assertThat(classificationService.classify("FNAC PARIS")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Shopping");
        assertThat(classificationService.classify("ZALANDO")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Shopping");
    }

    @Test
    @DisplayName("Classification : santé")
    void classifiesHealth() {
        assertThat(classificationService.classify("PHARMACIE CENTRALE")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Santé");
        assertThat(classificationService.classify("OPTIC 2000")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Santé");
    }

    @Test
    @DisplayName("Classification : loisirs")
    void classifiesLeisure() {
        assertThat(classificationService.classify("UGC CINEMA")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Loisirs");
        assertThat(classificationService.classify("STEAM COMMUNITY")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Loisirs");
        assertThat(classificationService.classify("DECATHLON PARIS")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Loisirs");
    }

    @Test
    @DisplayName("Classification : frais bancaires")
    void classifiesBankFees() {
        assertThat(classificationService.classify("FRAIS DE GESTION TRIMESTRIEL")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Frais bancaires");
        assertThat(classificationService.classify("FRAIS BANCAIRES")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Frais bancaires");
    }

    @Test
    @DisplayName("Classification : revenus")
    void classifiesIncome() {
        assertThat(classificationService.classify("SALAIRE")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Revenus");
        assertThat(classificationService.classify("SALAIRE NET")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Revenus");
        assertThat(classificationService.classify("ALLOC FAMILIALE CAF")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Revenus");
    }

    @Test
    @DisplayName("Classification : virements")
    void classifiesTransfers() {
        assertThat(classificationService.classify("VIREMENT INTERNE")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Virements");
        assertThat(classificationService.classify("VIREMENT VERS COMPTE")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Virements");
        assertThat(classificationService.classify("000001 VIR INSTANTANE EMIS LOGITEL POUR: Rami BoursoBank 29 10 BQ BOUS CPT 00040826995")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Virements");
        assertThat(classificationService.classify("VIR INSTANTANE EMIS WERO POUR: MOHAMMED AMINE S")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Virements");
        assertThat(classificationService.classify(
                "000001 VIR INSTANTANE EMIS LOGITEL POUR: Rami BoursoBank",
                "000001 VIR INSTANTANE EMIS LOGITEL POUR RAMI BOURSOBANK",
                "Loisirs",
                "Restaurants, bars, discothèques"
        )).isPresent().get().extracting(Category::getName).isEqualTo("Virements");
    }

    @Test
    @DisplayName("Classification : logement")
    void classifiesHousing() {
        assertThat(classificationService.classify("LOYER APPT 15EME")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Logement");
        assertThat(classificationService.classify("CHARGES COLLECTIVES")).isPresent()
                .get().extracting(Category::getName).isEqualTo("Logement");
    }

    // -------------------------------------------------------------------------
    // 3. Libellés ambigus et absence de règle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Classification : libellé inconnu reste sans catégorie")
    void unknownLabelRemainsUncategorized() {
        Optional<Category> result = classificationService.classify("OPERATION INCONNUE XYZ");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Classification : libellé vide ou null reste sans catégorie")
    void emptyLabelRemainsUncategorized() {
        assertThat(classificationService.classify("")).isEmpty();
        assertThat(classificationService.classify(null)).isEmpty();
        assertThat(classificationService.classify("   ")).isEmpty();
    }

    @Test
    @DisplayName("Classification : un montant positif n'est pas automatiquement un revenu")
    void positiveAmountIsNotAutoIncome() {
        // Le moteur ne regarde que le libellé, pas le montant
        Optional<Category> result = classificationService.classify("PAIEMENT CB MARCHAND");
        // "PAIEMENT CB MARCHAND" ne match aucune règle fiable
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // 4. GET /v1/categories
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/categories retourne les catégories système et privées de l'utilisateur")
    void listCategoriesReturnsSystemAndUserCategories() throws Exception {
        User user = activeUser();

        // Créer une catégorie privée
        Category privateCat = Category.createUserCategory(user, "Mes Projets", CategoryType.EXPENSE, null);
        categoryRepository.saveAndFlush(privateCat);

        User other = activeUser();
        Category otherCat = Category.createUserCategory(other, "Secret", CategoryType.EXPENSE, null);
        categoryRepository.saveAndFlush(otherCat);

        String auth = bearer(jwtEncoder, user.getId());

        String response = mockMvc.perform(get("/v1/categories").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].system").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode array = objectMapper.readTree(response);
        boolean hasPrivate = false;
        boolean hasOtherPrivate = false;
        for (JsonNode node : array) {
            String name = node.get("name").asText();
            if ("Mes Projets".equals(name)) hasPrivate = true;
            if ("Secret".equals(name)) hasOtherPrivate = true;
        }
        assertThat(hasPrivate).isTrue();
        assertThat(hasOtherPrivate).isFalse();
    }

    @Test
    @DisplayName("GET /v1/categories exige un JWT")
    void listCategoriesRequiresAuth() throws Exception {
        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 5. Catégorisation automatique à l'import
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Import CSV : les transactions reçoivent une catégorie automatique")
    void importAssignsAutomaticCategory() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte cat");

        byte[] csv = utf8(statement(
                "04/03/2025;;CARREFOUR PARIS;-12,50",
                "05/03/2025;;MCDONALD LYON;-8,90",
                "06/03/2025;;SNCF OUIGO;-49,00",
                "07/03/2025;;OPERATION INCONNUE;-5,00"));

        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        // Vérifier les catégories assignées via SQL direct pour éviter les proxies Hibernate
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT t.normalized_label as label, c.name as cat_name, t.custom_category_flag as custom_flag " +
                        "FROM transactions t LEFT JOIN categories c ON t.category_id = c.id " +
                        "WHERE t.account_id = ? ORDER BY t.booking_date",
                account.getId());
        assertThat(rows).hasSize(4);

        for (Map<String, Object> row : rows) {
            String label = (String) row.get("label");
            String catName = (String) row.get("cat_name");
            Boolean customFlag = (Boolean) row.get("custom_flag");
            if (label.startsWith("CARREFOUR")) {
                assertThat(catName).isEqualTo("Courses");
                assertThat(customFlag).isFalse();
            } else if (label.startsWith("MCDONALD")) {
                assertThat(catName).isEqualTo("Restaurants");
                assertThat(customFlag).isFalse();
            } else if (label.startsWith("SNCF")) {
                assertThat(catName).isEqualTo("Transport");
                assertThat(customFlag).isFalse();
            } else if (label.contains("OPERATION INCONNUE")) {
                assertThat(catName).isNull();
            }
        }
    }

    @Test
    @DisplayName("Import CSV : second import du même CSV ne duplique pas et conserve les catégories")
    void duplicateImportDoesNotDuplicateAndKeepsCategories() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte dedup cat");

        byte[] csv = utf8(statement(
                "04/03/2025;;CARREFOUR PARIS;-12,50",
                "05/03/2025;;LECLERC NANTES;-25,00"));

        // Premier import
        String firstBody = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode firstJob = objectMapper.readTree(firstBody).get("importJob");
        assertThat(firstJob.get("insertedRows").asInt()).isEqualTo(2);

        // Second import du même CSV
        String secondBody = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode secondJob = objectMapper.readTree(secondBody).get("importJob");
        assertThat(secondJob.get("insertedRows").asInt()).isEqualTo(0);
        assertThat(secondJob.get("skippedRows").asInt()).isEqualTo(2);

        // Toujours 2 transactions, avec catégories (via SQL pour éviter les proxies)
        Long txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?", Long.class, account.getId());
        assertThat(txCount).isEqualTo(2);

        Long categorizedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions t JOIN categories c ON t.category_id = c.id " +
                        "WHERE t.account_id = ? AND c.name = 'Courses'", Long.class, account.getId());
        assertThat(categorizedCount).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // 6. Correction manuelle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /v1/transactions/{id}/category : correction manuelle réussie")
    void manualCategoryAssignment() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte manuel");

        byte[] csv = utf8(statement("04/03/2025;;CARREFOUR PARIS;-12,50"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        // Récupérer la transaction
        String txResponse = mockMvc.perform(get("/v1/transactions")
                        .param("accountId", account.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String txId = objectMapper.readTree(txResponse).get("content").get(0).get("id").asText();

        // La catégorie automatique est Courses
        mockMvc.perform(get("/v1/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customCategoryFlag").value(false));

        // Changer manuellement vers Restaurants
        Category restaurants = findSystemCategory("Restaurants");

        mockMvc.perform(patch("/v1/transactions/" + txId + "/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + restaurants.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(restaurants.getId().toString()))
                .andExpect(jsonPath("$.customCategoryFlag").value(true));

        // Vérifier en base
        Transaction tx = transactionRepository.findWithCategoryById(UUID.fromString(txId)).orElseThrow();
        assertThat(tx.getCategory().getName()).isEqualTo("Restaurants");
        assertThat(tx.isCustomCategoryFlag()).isTrue();
        // Montant et date inchangés
        assertThat(tx.getAmountMinor()).isEqualTo(-1250L);
    }

    @Test
    @DisplayName("PATCH : suppression volontaire de la catégorie")
    void removeCategory() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte remove");

        byte[] csv = utf8(statement("04/03/2025;;CARREFOUR PARIS;-12,50"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        String txResponse = mockMvc.perform(get("/v1/transactions")
                        .param("accountId", account.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andReturn().getResponse().getContentAsString();
        String txId = objectMapper.readTree(txResponse).get("content").get(0).get("id").asText();

        // Supprimer la catégorie
        mockMvc.perform(patch("/v1/transactions/" + txId + "/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.customCategoryFlag").value(true));
    }

    @Test
    @DisplayName("PATCH : refus de modifier une transaction étrangère")
    void rejectForeignTransactionModification() throws Exception {
        User owner = activeUser();
        User intruder = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, owner, "Compte protégé");

        byte[] csv = utf8(statement("04/03/2025;;CARREFOUR PARIS;-12,50"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(owner, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        String txResponse = mockMvc.perform(get("/v1/transactions")
                        .param("accountId", account.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andReturn().getResponse().getContentAsString();
        String txId = objectMapper.readTree(txResponse).get("content").get(0).get("id").asText();

        Category courses = findSystemCategory("Courses");

        mockMvc.perform(patch("/v1/transactions/" + txId + "/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, intruder.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + courses.getId() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH : refus de catégorie privée étrangère")
    void rejectForeignPrivateCategory() throws Exception {
        User owner = activeUser();
        User other = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, owner, "Compte cat privée");

        // Catégorie privée de l'autre utilisateur
        Category foreignCat = Category.createUserCategory(other, "Secret", CategoryType.EXPENSE, null);
        categoryRepository.saveAndFlush(foreignCat);

        byte[] csv = utf8(statement("04/03/2025;;CARREFOUR PARIS;-12,50"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(owner, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        String txResponse = mockMvc.perform(get("/v1/transactions")
                        .param("accountId", account.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andReturn().getResponse().getContentAsString();
        String txId = objectMapper.readTree(txResponse).get("content").get(0).get("id").asText();

        mockMvc.perform(patch("/v1/transactions/" + txId + "/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + foreignCat.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 7. Préservation de customCategoryFlag lors du reclassement
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Reclassement : les transactions manuelles ne sont pas écrasées")
    void reclassificationSkipsManualTransactions() {
        User user = activeUser();

        // Créer les données via SQL direct pour éviter les contraintes JPA
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO accounts (id, user_id, name, source_type, currency) VALUES (?,?,?,?,?)",
                accountId, user.getId(), "Compte reclasse", "CSV", "EUR");

        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO import_jobs (id, user_id, account_id, file_hash, status, total_rows, inserted_rows, skipped_rows, failed_rows) VALUES (?,?,?,?,?,?,?,?,?)",
                jobId, user.getId(), accountId, "hash-test", "COMPLETED", 1, 1, 0, 0);

        UUID txId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transactions (id, account_id, import_job_id, fingerprint, booking_date, amount_minor, currency, raw_label, normalized_label, transaction_type, category_id, custom_category_flag) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                txId, accountId, jobId, "fp-manual", java.time.LocalDate.now(), -1000L, "EUR",
                "CARREFOUR", "CARREFOUR", "EXPENSE", null, false);

        // Assigner manuellement une catégorie différente (Loisirs au lieu de Courses)
        Category loisirs = findSystemCategory("Loisirs");
        jdbcTemplate.update(
                "UPDATE transactions SET category_id = ?, custom_category_flag = true WHERE id = ?",
                loisirs.getId(), txId);

        // Relancer le reclassement
        int changed = reclassificationService.reclassifyForUser(user.getId());
        assertThat(changed).isZero();

        // La catégorie manuelle est préservée
        String catName = jdbcTemplate.queryForObject(
                "SELECT c.name FROM transactions t JOIN categories c ON t.category_id = c.id WHERE t.id = ?",
                String.class, txId);
        assertThat(catName).isEqualTo("Loisirs");
    }

    // -------------------------------------------------------------------------
    // 8. Reclassement idempotent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Reclassement idempotent : deux exécutions consécutives ne modifient pas la deuxième fois")
    void reclassificationIsIdempotent() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte idempotent");

        byte[] csv = utf8(statement(
                "04/03/2025;;CARREFOUR PARIS;-12,50",
                "05/03/2025;;MCDONALD LYON;-8,90"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        // Premier reclassement – les transactions sont déjà catégorisées à l'import
        int first = reclassificationService.reclassifyForUser(user.getId());
        assertThat(first).isZero(); // Déjà catégorisées à l'import

        // Second reclassement – idempotent
        int second = reclassificationService.reclassifyForUser(user.getId());
        assertThat(second).isZero();
    }

    // -------------------------------------------------------------------------
    // 9. Conservation des montants, dates et fingerprints
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Catégorisation : montant, date et fingerprint ne sont pas modifiés")
    void classificationPreservesFinancialData() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte integrite");

        byte[] csv = utf8(statement("04/03/2025;05/03/2025;CARREFOUR PARIS;-12,50"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());

        // Vérifier via SQL pour éviter les proxies
        Long amount = jdbcTemplate.queryForObject(
                "SELECT amount_minor FROM transactions WHERE account_id = ?", Long.class, account.getId());
        assertThat(amount).isEqualTo(-1250L);

        String bookingDate = jdbcTemplate.queryForObject(
                "SELECT booking_date FROM transactions WHERE account_id = ?", String.class, account.getId());
        assertThat(bookingDate).isEqualTo("2025-03-04");

        String valueDate = jdbcTemplate.queryForObject(
                "SELECT value_date FROM transactions WHERE account_id = ?", String.class, account.getId());
        assertThat(valueDate).isEqualTo("2025-03-05");

        String fingerprint = jdbcTemplate.queryForObject(
                "SELECT fingerprint FROM transactions WHERE account_id = ?", String.class, account.getId());
        assertThat(fingerprint).isNotBlank();

        // La catégorie est assignée
        String catName = jdbcTemplate.queryForObject(
                "SELECT c.name FROM transactions t JOIN categories c ON t.category_id = c.id WHERE t.account_id = ?",
                String.class, account.getId());
        assertThat(catName).isEqualTo("Courses");
    }

    // -------------------------------------------------------------------------
    // 10. OpenAPI
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OpenAPI documente les nouveaux endpoints catégories")
    void openApiDescribesCategoryEndpoints() throws Exception {
        User user = activeUser();
        String body = mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        JsonNode paths = root.get("paths");

        assertThat(paths.has("/v1/categories")).isTrue();
        assertThat(paths.get("/v1/categories").has("get")).isTrue();
        assertThat(paths.has("/v1/transactions/{id}/category")).isTrue();
        assertThat(paths.get("/v1/transactions/{id}/category").has("patch")).isTrue();
        assertThat(root.get("components").get("schemas").has("CategoryResponse")).isTrue();
        assertThat(root.get("components").get("schemas").has("AssignCategoryRequest")).isTrue();
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private User activeUser() {
        User user = new User("cat-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Category findSystemCategory(String name) {
        return categoryRepository.findAllBySystemFlagTrue().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("System category not found: " + name));
    }
}
