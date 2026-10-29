package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
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
import org.springframework.mail.SimpleMailMessage;
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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.bearer;
import static com.eparly.api.imports.ImportTestSupport.createAccount;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class AccountsTransactionsApiIntegrationTest {

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
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Création de compte valide, identité JWT autoritaire, liste et consultation")
    void createsListsAndGetsOwnAccount() throws Exception {
        User owner = activeUser();
        User other = activeUser();
        String body = mockMvc.perform(post("/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Compte courant  \",\"userId\":\"" + other.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Compte courant"))
                .andExpect(jsonPath("$.sourceType").value("CSV"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String accountId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(get("/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(accountId));
        mockMvc.perform(get("/v1/accounts/" + accountId).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Compte courant"));
        mockMvc.perform(get("/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, other.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Nom invalide et nom dupliqué sont rejetés")
    void rejectsInvalidAndDuplicateNames() throws Exception {
        User user = activeUser();
        String auth = bearer(jwtEncoder, user.getId());
        mockMvc.perform(post("/v1/accounts").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/accounts").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Compte\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/accounts").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"compte\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Renommage autorisé, compte étranger indistinguable d'un compte absent")
    void updatesOwnAccountAndHidesForeignAccount() throws Exception {
        User owner = activeUser();
        User intruder = activeUser();
        String id = createAccount(mockMvc, jwtEncoder, objectMapper, owner, "Courant").getId().toString();

        mockMvc.perform(patch("/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Principal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Principal"));
        String foreign = mockMvc.perform(patch("/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, intruder.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Volé\"}"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String absent = mockMvc.perform(patch("/v1/accounts/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, intruder.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Volé\"}"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreign).contains("Account not found");
        assertThat(absent).contains("Account not found");
    }

    @Test
    @DisplayName("La devise peut être ajustée avant import mais est verrouillée ensuite")
    void currencyCannotChangeAfterTransactions() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte devise");
        mockMvc.perform(patch("/v1/accounts/" + account.getId()).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"USD\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("USD"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper)
                .importCsv(user, account.getId(), utf8(statement("04/03/2025;;ACHAT;-1,00")), MAPPING_FR)
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/v1/accounts/" + account.getId()).header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"EUR\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Parcours réel création HTTP, preview, import, transactions et historique ; second utilisateur interdit")
    void fullImportFlowAndCrossUserIsolation() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@eparly.test";
        String password = "Correct-Horse-Battery-42!";
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"termsAccepted\":true,\"marketingConsent\":false}"))
                .andExpect(status().isCreated());
        var mailCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        Matcher tokenMatcher = Pattern.compile("(?m)^([0-9]{6})$").matcher(mailCaptor.getValue().getText());
        assertThat(tokenMatcher.find()).isTrue();
        mockMvc.perform(post("/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenMatcher.group(1) + "\"}"))
                .andExpect(status().isOk());
        String login = mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String authorization = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
        User authenticatedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(authenticatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        User other = activeUser();
        String accountResponse = mockMvc.perform(post("/v1/accounts").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Compte importé\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(accountResponse).get("id").asText());
        byte[] csv = utf8(statement(
                "04/03/2025;05/03/2025;ACHAT EXEMPLE;-45,50",
                "05/03/2025;;VIREMENT RECU;1 250,00"));
        ImportHttpClient http = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper);
        String preview = http.preview(authorization, accountId, csv, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(preview).get("fileHash").asText()).isNotBlank();
        JsonNode importJob = http.importOk(authorization, accountId, csv, MAPPING_FR);
        assertThat(importJob.get("insertedRows").asInt()).isEqualTo(2);

        String transactions = mockMvc.perform(get("/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amountMinor").value(125000))
                .andExpect(jsonPath("$.content[0].rawLabel").value("VIREMENT RECU"))
                .andExpect(jsonPath("$.content[0].transactionType").value("UNKNOWN"))
                .andExpect(jsonPath("$.content[0].categoryId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].customCategoryFlag").value(false))
                .andExpect(jsonPath("$.content[0].fingerprint").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode tx = objectMapper.readTree(transactions).get("content").get(0);
        String transactionId = tx.get("id").asText();

        mockMvc.perform(get("/v1/transactions/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.rawLabel").value("VIREMENT RECU"));

        mockMvc.perform(get("/v1/accounts/" + accountId + "/imports")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/v1/transactions/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, other.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/accounts/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, other.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/transactions").param("accountId", accountId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, other.getId())))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from transactions where account_id = ?",
                Long.class, accountId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Filtres combinés, pagination plafonnée et ordre déterministe")
    void filtersPaginationAndStableOrdering() throws Exception {
        User user = activeUser();
        var account = createAccount(mockMvc, jwtEncoder, objectMapper, user, "Compte filtres");
        byte[] csv = utf8(statement(
                "04/03/2025;;ACHAT A;-4,50",
                "05/03/2025;;CREDIT;10,00",
                "06/03/2025;;ACHAT B;-5,00",
                "06/03/2025;;ACHAT C;-6,00"));
        new ImportHttpClient(mockMvc, jwtEncoder, objectMapper).importCsv(user, account.getId(), csv, MAPPING_FR)
                .andExpect(status().isCreated());
        String auth = bearer(jwtEncoder, user.getId());

        mockMvc.perform(get("/v1/transactions").param("accountId", account.getId().toString())
                        .param("startDate", "2025-03-05").param("endDate", "2025-03-06")
                        .param("transactionType", "UNKNOWN").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rawLabel").value("CREDIT"));

        String firstPage = mockMvc.perform(get("/v1/transactions").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].bookingDate").value("2025-03-06"))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalElements").value(4))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(firstPage).get("content");
        assertThat(content.get(0).get("id").asText()).isGreaterThan(content.get(1).get("id").asText());

        mockMvc.perform(get("/v1/transactions").param("size", "1000")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/v1/transactions").param("startDate", "2025-03-07")
                        .param("endDate", "2025-03-01").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Toutes les routes privées exigent un JWT")
    void routesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Compte\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/accounts/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/v1/accounts/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Compte\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/transactions/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private User activeUser() {
        User user = new User("api-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("OpenAPI documente les routes, les schémas et la pagination des nouveaux endpoints")
    void openApiDescribesAccountsAndTransactions() throws Exception {
        User user = activeUser();
        String body = mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        JsonNode paths = root.get("paths");

        assertThat(paths.has("/v1/accounts")).isTrue();
        assertThat(paths.get("/v1/accounts").has("post")).isTrue();
        assertThat(paths.get("/v1/accounts").has("get")).isTrue();
        assertThat(paths.get("/v1/accounts/{id}").has("get")).isTrue();
        assertThat(paths.get("/v1/accounts/{id}").has("patch")).isTrue();
        assertThat(paths.get("/v1/transactions").get("get").toString())
                .contains("accountId", "startDate", "endDate", "transactionType", "page", "size");
        assertThat(paths.get("/v1/transactions/{id}").has("get")).isTrue();
        assertThat(root.get("components").get("schemas").has("CreateAccountRequest")).isTrue();
        assertThat(root.get("components").get("schemas").has("UpdateAccountRequest")).isTrue();
        assertThat(root.get("components").get("schemas").has("TransactionResponse")).isTrue();
        assertThat(root.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asText())
                .isEqualTo("bearer");
    }
}
