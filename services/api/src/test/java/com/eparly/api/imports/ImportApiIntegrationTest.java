package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.bearer;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class ImportApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------ previsualisation

    @Test
    @DisplayName("Preview : hash, mapping propose, echantillon, estimation, avertissement d'ambiguite et aucune ecriture en base")
    void previewReturnsReportAndWritesNothing() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement(
                "04/03/2025;05/03/2025;PAIEMENT CB EXEMPLE;-45,50",
                "06/03/2025;06/03/2025;VIREMENT RECU;1 250,00",
                "06/03/2025;;CAFE;-2,50",
                "06/03/2025;;CAFE;-2,50"));

        mockMvc.perform(previewRequest(user, account.getId(), csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileHash").value(sha256(csv)))
                .andExpect(jsonPath("$.detectedCharset").value("UTF_8"))
                .andExpect(jsonPath("$.charsetAmbiguous").value(false))
                .andExpect(jsonPath("$.detectedDelimiter").value(";"))
                .andExpect(jsonPath("$.headers.length()").value(4))
                .andExpect(jsonPath("$.suggestedMapping.dateColumn").value("Date operation"))
                .andExpect(jsonPath("$.suggestedMapping.valueDateColumn").value("Date valeur"))
                .andExpect(jsonPath("$.suggestedMapping.amountColumn").value("Montant"))
                .andExpect(jsonPath("$.suggestedMapping.dateFormat").value("dd/MM/yyyy"))
                .andExpect(jsonPath("$.mappingComplete").value(true))
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.wouldInsert").value(4))
                .andExpect(jsonPath("$.wouldSkip").value(0))
                .andExpect(jsonPath("$.blocking").value(false))
                .andExpect(jsonPath("$.sample.length()").value(4))
                .andExpect(jsonPath("$.sample[0].line").value(2))
                .andExpect(jsonPath("$.sample[0].bookingDate").value("2025-03-04"))
                .andExpect(jsonPath("$.sample[0].valueDate").value("2025-03-05"))
                .andExpect(jsonPath("$.sample[0].amountMinor").value(-4550))
                .andExpect(jsonPath("$.sample[0].currency").value("EUR"))
                .andExpect(jsonPath("$.sample[1].amountMinor").value(125000))
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andExpect(jsonPath("$.warnings[?(@.code=='AMBIGUOUS_IDENTICAL_ROWS')]").isNotEmpty())
                .andExpect(jsonPath("$.truncated").value(false));

        assertThat(count("transactions", account.getId())).isZero();
        assertThat(count("import_jobs", account.getId())).isZero();
    }

    @Test
    @DisplayName("Preview : erreurs bloquantes numerotees par ligne et colonne, sans la valeur fautive")
    void previewReportsRowErrorsWithLineNumbers() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement(
                "04/03/2025;;OK;-1,00",
                "31/02/2025;;DATE IMPOSSIBLE;-1,00",
                "05/03/2025;;MONTANT TEXTE;abc",
                "06/03/2025;;MONTANT AMBIGU;1,250"));

        String body = mockMvc.perform(previewRequest(user, account.getId(), csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocking").value(true))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[0].column").value("Date operation"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_DATE"))
                .andExpect(jsonPath("$.errors[1].line").value(4))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.errors[2].line").value(5))
                .andExpect(jsonPath("$.errors[2].code").value("AMBIGUOUS_AMOUNT"))
                .andExpect(jsonPath("$.sample.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("31/02/2025").doesNotContain("abc");
    }

    @Test
    @DisplayName("Preview : encodage Windows-1252 signale comme ambigu avec les candidats, ou resolu par un charset explicite")
    void previewFlagsAmbiguousCharset() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = statement("04/03/2025;;CAF\u00c9 CR\u00c8ME;-2,50").getBytes(Charset.forName("windows-1252"));

        mockMvc.perform(previewRequest(user, account.getId(), csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charsetAmbiguous").value(true))
                .andExpect(jsonPath("$.charsetCandidates[0]").value("WINDOWS_1252"))
                .andExpect(jsonPath("$.charsetCandidates[1]").value("ISO_8859_1"))
                .andExpect(jsonPath("$.warnings[?(@.code=='CHARSET_AMBIGUOUS')]").isNotEmpty());

        mockMvc.perform(previewRequest(user, account.getId(), csv).param("charset", "WINDOWS_1252"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charsetAmbiguous").value(false))
                .andExpect(jsonPath("$.sample[0].label").value("CAF\u00c9 CR\u00c8ME"));
    }

    @Test
    @DisplayName("Preview : mapping personnalise applique, mapping avec colonne inconnue signale")
    void previewAppliesCustomMapping() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8("Jour;Intitul\u00e9;Somme\n04/03/2025;LOYER;-650,00\n");
        String mapping = "{\"dateColumn\":\"jour\",\"labelColumn\":\"INTITULE\",\"amountColumn\":\"somme\","
                + "\"dateFormat\":\"dd/MM/yyyy\",\"amountFormat\":\"FR\"}";

        mockMvc.perform(previewRequest(user, account.getId(), csv).param("mapping", mapping))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappingComplete").value(true))
                .andExpect(jsonPath("$.appliedMapping.amountFormat").value("FR"))
                .andExpect(jsonPath("$.sample[0].amountMinor").value(-65000));

        String unknown = "{\"dateColumn\":\"Inexistante\",\"labelColumn\":\"Intitul\u00e9\",\"amountColumn\":\"Somme\"}";
        mockMvc.perform(previewRequest(user, account.getId(), csv).param("mapping", unknown))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappingComplete").value(false))
                .andExpect(jsonPath("$.blocking").value(true))
                .andExpect(jsonPath("$.errors[?(@.code=='MAPPING_COLUMN_NOT_FOUND')]").isNotEmpty());
    }

    @Test
    @DisplayName("Preview : colonnes debit/credit proposees automatiquement")
    void previewSupportsDebitCreditColumns() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8("Date;Libelle;Debit;Credit\n04/03/2025;ACHAT;45,50;\n05/03/2025;SALAIRE;;1 500,00\n");

        mockMvc.perform(previewRequest(user, account.getId(), csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedMapping.debitColumn").value("Debit"))
                .andExpect(jsonPath("$.suggestedMapping.creditColumn").value("Credit"))
                .andExpect(jsonPath("$.blocking").value(false))
                .andExpect(jsonPath("$.sample[0].amountMinor").value(-4550))
                .andExpect(jsonPath("$.sample[1].amountMinor").value(150000));
    }

    // ------------------------------------------------------------------ securite

    @Test
    @DisplayName("Preview sans JWT : 401")
    void previewRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/imports/preview")
                        .file(new MockMultipartFile("file", "releve.csv", "text/csv", utf8(statement("04/03/2025;;X;-1,00"))))
                        .param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Preview sur le compte d'un autre utilisateur ou un compte inconnu : meme 404 neutre")
    void previewOnForeignOrUnknownAccountIsNeutral404() throws Exception {
        User owner = activeUser();
        User intruder = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(owner, "Compte prive"));
        byte[] csv = utf8(statement("04/03/2025;;X;-1,00"));

        String foreign = mockMvc.perform(previewRequest(intruder, account.getId(), csv))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(previewRequest(intruder, UUID.randomUUID(), csv))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(foreign).contains("Account not found").doesNotContain(account.getId().toString());
        assertThat(unknown).contains("Account not found");
    }

    @Test
    @DisplayName("Preview par un utilisateur non actif : 403")
    void previewRejectsInactiveUser() throws Exception {
        User user = userRepository.saveAndFlush(new User("imp-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!"));
        assertThat(user.getStatus()).isNotEqualTo(UserStatus.ACTIVE);
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte"));

        mockMvc.perform(previewRequest(user, account.getId(), utf8(statement("04/03/2025;;X;-1,00"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ erreurs HTTP standardisees

    @Test
    @DisplayName("Fichier vide : 400, fichier binaire renomme .csv : 415, fichier trop gros : 413")
    void previewRejectsUnusableFiles() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));

        mockMvc.perform(previewRequest(user, account.getId(), new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/v1/imports/preview"));

        byte[] zip = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0};
        mockMvc.perform(previewRequest(user, account.getId(), zip))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("BINARY_FILE"));

        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        Arrays.fill(tooLarge, (byte) 'a');
        mockMvc.perform(previewRequest(user, account.getId(), tooLarge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        mockMvc.perform(previewRequest(user, account.getId(), utf8("Un texte libre sans separateur\nsur deux lignes\n")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("NOT_CSV"));

        mockMvc.perform(previewRequest(user, account.getId(), utf8("Date;Libelle;Montant\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_DATA_ROWS"));
    }

    @Test
    @DisplayName("Parametres invalides : fichier manquant, accountId invalide, mapping/charset/separateur invalides -> 400")
    void previewRejectsInvalidParameters() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement("04/03/2025;;X;-1,00"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/imports/preview")
                        .param("accountId", account.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/imports/preview")
                        .file(new MockMultipartFile("file", "releve.csv", "text/csv", csv))
                        .param("accountId", "pas-un-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(previewRequest(user, account.getId(), csv).param("mapping", "{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MAPPING"));

        mockMvc.perform(previewRequest(user, account.getId(), csv).param("mapping", "{\"amountFormat\":\"XX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MAPPING"));

        mockMvc.perform(previewRequest(user, account.getId(), csv).param("charset", "KLINGON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHARSET"));

        mockMvc.perform(previewRequest(user, account.getId(), csv).param("delimiter", "|"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DELIMITER"));
    }

    @Test
    @DisplayName("Les erreurs Spring MVC (route inconnue) ne deviennent pas des 500 et ne fuient aucune trace")
    void unknownRouteStaysNotFound() throws Exception {
        User user = activeUser();

        String body = mockMvc.perform(get("/v1/imports/n-existe-pas/vraiment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception").doesNotContain(".java").doesNotContain("at com.");
    }

    @Test
    @DisplayName("Mapping FR explicite : le mapping de reference des tests est accepte")
    void referenceMappingIsAccepted() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));

        mockMvc.perform(previewRequest(user, account.getId(), utf8(statement("04/03/2025;;LOYER;-650,00")))
                        .param("mapping", MAPPING_FR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappingComplete").value(true))
                .andExpect(jsonPath("$.blocking").value(false));
    }

    // ------------------------------------------------------------------ import confirme

    @Test
    @DisplayName("Import valide : transactions persistees en centimes, job COMPLETED, consultable dans l'historique")
    void importPersistsTransactionsAndTracesJob() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement(
                "04/03/2025;05/03/2025;PAIEMENT CB EXEMPLE;-45,50",
                "06/03/2025;;VIREMENT RECU;1 250,00",
                "07/03/2025;;REMBOURSEMENT;+350,20"));

        String body = mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importJob.status").value("COMPLETED"))
                .andExpect(jsonPath("$.importJob.accountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.importJob.totalRows").value(3))
                .andExpect(jsonPath("$.importJob.insertedRows").value(3))
                .andExpect(jsonPath("$.importJob.skippedRows").value(0))
                .andExpect(jsonPath("$.importJob.failedRows").value(0))
                .andExpect(jsonPath("$.importJob.completedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(body).get("importJob").get("id").asText();

        assertThat(count("transactions", account.getId())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "SELECT amount_minor FROM transactions WHERE account_id = ? ORDER BY booking_date", Long.class, account.getId()))
                .containsExactly(-4550L, 125000L, 35020L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT transaction_type FROM transactions WHERE account_id = ? ORDER BY booking_date", String.class, account.getId()))
                .containsExactly("EXPENSE", "UNKNOWN", "UNKNOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND import_job_id = ?::uuid AND category_id IS NULL",
                Long.class, account.getId(), jobId)).isEqualTo(3);
        assertThat(body).doesNotContain("PAIEMENT CB EXEMPLE").doesNotContain("4550");

        mockMvc.perform(get("/v1/imports/" + jobId).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.insertedRows").value(3));
        mockMvc.perform(get("/v1/imports").header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobId));
        mockMvc.perform(get("/v1/accounts/" + account.getId() + "/imports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(jobId));
    }

    @Test
    @DisplayName("Reimport du meme fichier : job COMPLETED, 0 insere, N ignores")
    void reimportingSameFileInsertsNothing() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement("04/03/2025;;A;-1,00", "05/03/2025;;B;-2,00"));

        mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importJob.insertedRows").value(2));
        mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importJob.status").value("COMPLETED"))
                .andExpect(jsonPath("$.importJob.totalRows").value(2))
                .andExpect(jsonPath("$.importJob.insertedRows").value(0))
                .andExpect(jsonPath("$.importJob.skippedRows").value(2));

        assertThat(count("transactions", account.getId())).isEqualTo(2);
        assertThat(count("import_jobs", account.getId())).isEqualTo(2);
        mockMvc.perform(previewRequest(user, account.getId(), csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wouldInsert").value(0))
                .andExpect(jsonPath("$.wouldSkip").value(2));
    }

    @Test
    @DisplayName("Hash different de celui du fichier : 409 FILE_HASH_MISMATCH, rien n'est cree")
    void importRejectsHashMismatch() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] previewed = utf8(statement("04/03/2025;;A;-1,00"));
        byte[] modified = utf8(statement("04/03/2025;;A;-9999,00"));

        mockMvc.perform(importRequest(user, account.getId(), modified, MAPPING_FR, sha256(previewed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_HASH_MISMATCH"));

        assertThat(count("transactions", account.getId())).isZero();
        assertThat(count("import_jobs", account.getId())).isZero();
    }

    @Test
    @DisplayName("Hash ou mapping absents : 400 ; mapping avec dateFormat AUTO refuse a l'import")
    void importRequiresHashAndExplicitMapping() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement("04/03/2025;;A;-1,00"));

        mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_HASH_REQUIRED"));
        mockMvc.perform(importRequest(user, account.getId(), csv, null, sha256(csv)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAPPING_REQUIRED"));

        String autoDate = MAPPING_FR.replace("\"dateFormat\":\"dd/MM/yyyy\"", "\"dateFormat\":\"AUTO\"");
        mockMvc.perform(importRequest(user, account.getId(), csv, autoDate, sha256(csv)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("MAPPING_DATE_FORMAT_REQUIRED"));
        assertThat(count("transactions", account.getId())).isZero();
    }

    @Test
    @DisplayName("Erreurs bloquantes : 422 avec rapport, aucune transaction, ImportJob FAILED trace sans donnee sensible")
    void importIsAtomicAndTracesFailedJob() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement(
                "04/03/2025;;LIBELLE SECRET UN;-1,00",
                "31/02/2025;;LIBELLE SECRET DEUX;-2,00",
                "06/03/2025;;LIBELLE SECRET TROIS;abc"));

        String body = mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.path").value("/v1/imports"))
                .andExpect(jsonPath("$.importJobId").isNotEmpty())
                .andExpect(jsonPath("$.issueCount").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_DATE"))
                .andExpect(jsonPath("$.errors[1].line").value(4))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_AMOUNT"))
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(body).get("importJobId").asText();

        assertThat(count("transactions", account.getId())).isZero();
        mockMvc.perform(get("/v1/imports/" + jobId).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.insertedRows").value(0))
                .andExpect(jsonPath("$.failedRows").value(2))
                .andExpect(jsonPath("$.errorSummary").value(org.hamcrest.Matchers.containsString("INVALID_DATE")));
        String summary = jdbcTemplate.queryForObject("SELECT error_summary FROM import_jobs WHERE id = ?::uuid", String.class, jobId);
        assertThat(summary).doesNotContain("SECRET").doesNotContain("31/02");
        assertThat(body).doesNotContain("SECRET");
    }

    @Test
    @DisplayName("Encodage ambigu : 400 CHARSET_REQUIRED a l'import tant que le charset n'est pas explicite")
    void importRequiresExplicitCharsetWhenAmbiguous() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = statement("04/03/2025;;CAF\u00c9;-2,50").getBytes(Charset.forName("windows-1252"));

        mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHARSET_REQUIRED"));
        assertThat(count("transactions", account.getId())).isZero();

        mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)).param("charset", "WINDOWS_1252"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importJob.insertedRows").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_label FROM transactions WHERE account_id = ?", String.class, account.getId()))
                .isEqualTo("CAF\u00c9");
    }

    @Test
    @DisplayName("Import dans le compte d'un autre utilisateur : 404, sans JWT : 401, aucune ecriture")
    void importIsIsolatedBetweenUsers() throws Exception {
        User owner = activeUser();
        User intruder = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(owner, "Compte prive"));
        byte[] csv = utf8(statement("04/03/2025;;A;-1,00"));

        mockMvc.perform(importRequest(intruder, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/imports")
                        .file(new MockMultipartFile("file", "releve.csv", "text/csv", csv))
                        .param("accountId", account.getId().toString())
                        .param("mapping", MAPPING_FR)
                        .param("fileHash", sha256(csv)))
                .andExpect(status().isUnauthorized());

        assertThat(count("transactions", account.getId())).isZero();
        assertThat(count("import_jobs", account.getId())).isZero();
    }

    // ------------------------------------------------------------------ historique

    @Test
    @DisplayName("Historique : uniquement les imports de l'utilisateur, detail d'un autre utilisateur en 404, compte etranger en 404")
    void historyIsScopedToCurrentUser() throws Exception {
        User owner = activeUser();
        User other = activeUser();
        Account ownerAccount = accountRepository.saveAndFlush(new Account(owner, "Compte A"));
        Account otherAccount = accountRepository.saveAndFlush(new Account(other, "Compte B"));
        byte[] ownerCsv = utf8(statement("04/03/2025;;A;-1,00"));
        byte[] otherCsv = utf8(statement("04/03/2025;;B;-2,00"));

        String ownerJob = importedJobId(owner, ownerAccount, ownerCsv);
        String otherJob = importedJobId(other, otherAccount, otherCsv);

        mockMvc.perform(get("/v1/imports").header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownerJob));
        mockMvc.perform(get("/v1/imports/" + otherJob).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/imports/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/accounts/" + otherAccount.getId() + "/imports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/imports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/imports/" + ownerJob)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/imports/pas-un-uuid").header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Historique : pagination, tri createdAt decroissant, taille plafonnee a 100, parametres invalides en 400")
    void historyIsPaginatedAndSortedNewestFirst() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        List<String> jobIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            jobIds.add(importedJobId(user, account, utf8(statement("0" + i + "/03/2025;;LIGNE " + i + ";-1,00"))));
        }
        String auth = bearer(jwtEncoder, user.getId());

        mockMvc.perform(get("/v1/imports").param("size", "2").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(jobIds.get(2)))
                .andExpect(jsonPath("$.content[1].id").value(jobIds.get(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/v1/accounts/" + account.getId() + "/imports").param("page", "1").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobIds.get(0)));
        mockMvc.perform(get("/v1/imports").param("size", "1000").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/v1/imports").param("page", "-1").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/imports").param("size", "0").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/imports").param("size", "abc").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ utilitaires

    private String importedJobId(User user, Account account, byte[] csv) throws Exception {
        String body = mockMvc.perform(importRequest(user, account.getId(), csv, MAPPING_FR, sha256(csv)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("importJob").get("id").asText();
    }

    private MockMultipartHttpServletRequestBuilder importRequest(
            User user, UUID accountId, byte[] content, String mapping, String fileHash
    ) {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/v1/imports");
        builder.file(new MockMultipartFile("file", "releve.csv", "text/csv", content));
        builder.param("accountId", accountId.toString());
        if (mapping != null) {
            builder.param("mapping", mapping);
        }
        if (fileHash != null) {
            builder.param("fileHash", fileHash);
        }
        builder.header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()));
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder previewRequest(User user, UUID accountId, byte[] content) {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/v1/imports/preview");
        builder.file(new MockMultipartFile("file", "releve.csv", "text/csv", content));
        builder.param("accountId", accountId.toString());
        builder.header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()));
        return builder;
    }

    private User activeUser() {
        User user = new User("imp-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private long count(String table, UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE account_id = ?", Long.class, accountId);
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
