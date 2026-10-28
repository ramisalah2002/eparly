package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.createAccount;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Strategie de deduplication v1 : empreinte de contenu avec rang d'occurrence, ou identifiant source fiable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class ImportDeduplicationIntegrationTest {

    private static final String MAPPING_WITH_UID = "{\"dateColumn\":\"Date operation\",\"labelColumn\":\"Libell\u00e9\","
            + "\"amountColumn\":\"Montant\",\"sourceIdColumn\":\"Id transaction\",\"dateFormat\":\"dd/MM/yyyy\","
            + "\"amountFormat\":\"FR\"}";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ImportHttpClient http;
    private User user;
    private Account account;

    @BeforeEach
    void setUp() throws Exception {
        http = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper);
        User created = new User("dedup-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        created.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(created);
        account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte courant");
    }

    @Test
    @DisplayName("Deux achats legitimes identiques (meme date, montant, libelle) coexistent")
    void identicalLegitimatePurchasesCoexist() throws Exception {
        byte[] csv = utf8(statement(
                "04/03/2025;;CAFE DE LA GARE;-2,50",
                "04/03/2025;;CAFE DE LA GARE;-2,50"));

        JsonNode job = http.importOk(user, account.getId(), csv, MAPPING_FR);

        assertThat(job.get("insertedRows").asInt()).isEqualTo(2);
        assertThat(count()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT fingerprint) FROM transactions WHERE account_id = ?", Long.class, account.getId()))
                .isEqualTo(2);
        http.preview(user, account.getId(), csv, null)
                .andExpect(jsonPath("$.warnings[?(@.code=='AMBIGUOUS_IDENTICAL_ROWS')]").isNotEmpty())
                .andExpect(jsonPath("$.wouldSkip").value(2));
    }

    @Test
    @DisplayName("Releves qui se chevauchent : seules les operations nouvelles sont inserees")
    void overlappingStatementsInsertOnlyNewOperations() throws Exception {
        byte[] first = utf8(statement(
                "01/03/2025;;LOYER;-650,00",
                "02/03/2025;;COURSES;-45,10",
                "03/03/2025;;ESSENCE;-60,00",
                "04/03/2025;;CINEMA;-12,00"));
        byte[] second = utf8(statement(
                "03/03/2025;;ESSENCE;-60,00",
                "04/03/2025;;CINEMA;-12,00",
                "05/03/2025;;RESTAURANT;-30,00",
                "06/03/2025;;SALAIRE;1 800,00"));

        assertThat(http.importOk(user, account.getId(), first, MAPPING_FR).get("insertedRows").asInt()).isEqualTo(4);
        http.preview(user, account.getId(), second, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wouldInsert").value(2))
                .andExpect(jsonPath("$.wouldSkip").value(2));
        JsonNode job = http.importOk(user, account.getId(), second, MAPPING_FR);

        assertThat(job.get("totalRows").asInt()).isEqualTo(4);
        assertThat(job.get("insertedRows").asInt()).isEqualTo(2);
        assertThat(job.get("skippedRows").asInt()).isEqualTo(2);
        assertThat(count()).isEqualTo(6);
    }

    @Test
    @DisplayName("Ordre des lignes different entre deux exports (doublons identiques compris) : aucune insertion")
    void reorderedStatementIsRecognised() throws Exception {
        List<String> lines = List.of(
                "01/03/2025;;LOYER;-650,00",
                "02/03/2025;;CAFE;-2,50",
                "02/03/2025;;CAFE;-2,50",
                "03/03/2025;;ESSENCE;-60,00");
        byte[] original = utf8(statement(lines.toArray(String[]::new)));
        List<String> reversed = new java.util.ArrayList<>(lines);
        Collections.reverse(reversed);
        byte[] reordered = utf8(statement(reversed.toArray(String[]::new)));

        http.importOk(user, account.getId(), original, MAPPING_FR);
        JsonNode job = http.importOk(user, account.getId(), reordered, MAPPING_FR);

        assertThat(job.get("insertedRows").asInt()).isZero();
        assertThat(job.get("skippedRows").asInt()).isEqualTo(4);
        assertThat(count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Limite documentee : un export B avec plus d'occurrences identiques que A insere les surnumeraires, avec moins il n'insere rien")
    void identicalOccurrencesBeyondPreviousImportAreInserted() throws Exception {
        byte[] twoCoffees = utf8(statement("02/03/2025;;CAFE;-2,50", "02/03/2025;;CAFE;-2,50"));
        byte[] threeCoffees = utf8(statement("02/03/2025;;CAFE;-2,50", "02/03/2025;;CAFE;-2,50", "02/03/2025;;CAFE;-2,50"));
        byte[] oneCoffee = utf8(statement("02/03/2025;;CAFE;-2,50"));

        http.importOk(user, account.getId(), twoCoffees, MAPPING_FR);
        JsonNode fewer = http.importOk(user, account.getId(), oneCoffee, MAPPING_FR);
        assertThat(fewer.get("insertedRows").asInt()).isZero();
        assertThat(fewer.get("skippedRows").asInt()).isEqualTo(1);

        JsonNode more = http.importOk(user, account.getId(), threeCoffees, MAPPING_FR);
        assertThat(more.get("insertedRows").asInt()).isEqualTo(1);
        assertThat(more.get("skippedRows").asInt()).isEqualTo(2);
        assertThat(count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Libelle accentue, en casse ou espacement differents entre deux exports : meme operation")
    void labelNormalisationMakesExportsComparable() throws Exception {
        byte[] first = utf8(statement("04/03/2025;;Caf\u00e9  de la Gare;-2,50"));
        byte[] second = utf8(statement("04/03/2025;;CAFE DE LA GARE;-2,50"));

        http.importOk(user, account.getId(), first, MAPPING_FR);
        JsonNode job = http.importOk(user, account.getId(), second, MAPPING_FR);

        assertThat(job.get("insertedRows").asInt()).isZero();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Date de valeur differente entre deux exports : meme operation")
    void valueDateIsExcludedFromFingerprint() throws Exception {
        byte[] first = utf8(statement("04/03/2025;05/03/2025;VIREMENT;-100,00"));
        byte[] second = utf8(statement("04/03/2025;06/03/2025;VIREMENT;-100,00"));

        http.importOk(user, account.getId(), first, MAPPING_FR);
        JsonNode job = http.importOk(user, account.getId(), second, MAPPING_FR);

        assertThat(job.get("insertedRows").asInt()).isZero();
    }

    @Test
    @DisplayName("Montant, date ou libelle differents : operations distinctes")
    void differentContentIsNotDeduplicated() throws Exception {
        http.importOk(user, account.getId(), utf8(statement("04/03/2025;;ACHAT;-10,00")), MAPPING_FR);

        JsonNode job = http.importOk(user, account.getId(), utf8(statement(
                "04/03/2025;;ACHAT;-10,01",
                "05/03/2025;;ACHAT;-10,00",
                "04/03/2025;;AUTRE ACHAT;-10,00")), MAPPING_FR);

        assertThat(job.get("insertedRows").asInt()).isEqualTo(3);
        assertThat(count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Identifiant source fiable : identite propre, independante du libelle et de la date")
    void reliableSourceIdIsTheIdentity() throws Exception {
        byte[] first = utf8("Date operation;Libell\u00e9;Montant;Id transaction\n"
                + "04/03/2025;PAIEMENT CB 1;-10,00;TX-001\n"
                + "05/03/2025;PAIEMENT CB 2;-20,00;TX-002\n");
        byte[] second = utf8("Date operation;Libell\u00e9;Montant;Id transaction\n"
                + "04/03/2025;LIBELLE REECRIT PAR LA BANQUE;-10,00;TX-001\n"
                + "05/03/2025;PAIEMENT CB 2;-20,00;TX-002\n"
                + "06/03/2025;NOUVELLE OPERATION;-5,00;TX-003\n");

        http.importOk(user, account.getId(), first, MAPPING_WITH_UID);
        JsonNode job = http.importOk(user, account.getId(), second, MAPPING_WITH_UID);

        assertThat(job.get("insertedRows").asInt()).isEqualTo(1);
        assertThat(job.get("skippedRows").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT source_uid FROM transactions WHERE account_id = ? ORDER BY source_uid", String.class, account.getId()))
                .containsExactly("TX-001", "TX-002", "TX-003");
    }

    @Test
    @DisplayName("Identifiant source repete dans un meme fichier : non fiable, import refuse (422) sans insertion")
    void duplicatedSourceIdInFileIsBlocking() throws Exception {
        byte[] csv = utf8("Date operation;Libell\u00e9;Montant;Id transaction\n"
                + "04/03/2025;A;-10,00;TX-001\n"
                + "05/03/2025;B;-20,00;TX-001\n");

        http.importCsv(user, account.getId(), csv, MAPPING_WITH_UID)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_SOURCE_ID"))
                .andExpect(jsonPath("$.errors[0].line").value(3));

        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("Deduplication cloisonnee par compte : meme contenu dans deux comptes distincts")
    void deduplicationIsScopedToTheAccount() throws Exception {
        Account other = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Livret");
        byte[] csv = utf8(statement("04/03/2025;;VIREMENT;-100,00"));

        http.importOk(user, account.getId(), csv, MAPPING_FR);
        JsonNode job = http.importOk(user, other.getId(), csv, MAPPING_FR);

        assertThat(job.get("insertedRows").asInt()).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?", Long.class, other.getId())).isEqualTo(1);
    }

    private long count() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?", Long.class, account.getId());
    }
}
