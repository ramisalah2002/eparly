package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.imports.ImportTestSupport;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class RecurringIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;

    private User activeUser() {
        User user = new User("rec-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(UUID userId) {
        return ImportTestSupport.bearer(jwtEncoder, userId);
    }

    private Account createAccount(User user, String name) throws Exception {
        return ImportTestSupport.createAccount(mockMvc, jwtEncoder, objectMapper, user, name);
    }

    private void importCsv(User user, Account account, String... lines) throws Exception {
        StringBuilder csv = new StringBuilder("Date operation;Date valeur;Libell\u00e9;Montant\n");
        for (String l : lines) csv.append(l).append('\n');
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        String hash = java.security.MessageDigest.getInstance("SHA-256").digest(content) == null ? "" : java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile("file", "r.csv", "text/csv", content);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/v1/imports")
                        .file(file)
                        .param("accountId", account.getId().toString())
                        .param("mapping", ImportTestSupport.MAPPING_FR)
                        .param("fileHash", hash)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Monthly stable subscription detected")
    void monthlyStable() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99",
                "05/04/2025;;NETFLIX;-15,99");

        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesCreated").value(1));

        mockMvc.perform(get("/v1/recurring/candidates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].seriesType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$[0].occurrenceCount").value(4))
                .andExpect(jsonPath("$[0].usualAmountMinor").value(1599));
    }

    @Test
    @DisplayName("Weekly detected")
    void weekly() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "01/01/2025;;SALLE SPORT;-10,00",
                "08/01/2025;;SALLE SPORT;-10,00",
                "15/01/2025;;SALLE SPORT;-10,00",
                "22/01/2025;;SALLE SPORT;-10,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequency").value("WEEKLY"));
    }

    @Test
    @DisplayName("Quarterly detected")
    void quarterly() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "01/01/2025;;EDF;-50,00",
                "01/04/2025;;EDF;-50,00",
                "01/07/2025;;EDF;-50,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequency").value("QUARTERLY"))
                .andExpect(jsonPath("$[0].seriesType").value("RECURRING_BILL"));
    }

    @Test
    @DisplayName("Slight amount variation")
    void amountVariation() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;SPOTIFY;-9,99",
                "05/02/2025;;SPOTIFY;-10,09",
                "05/03/2025;;SPOTIFY;-9,95",
                "05/04/2025;;SPOTIFY;-10,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"));
    }

    @Test
    @DisplayName("Calendar shift month end")
    void calendarShift() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "31/01/2025;;LOYER;-500,00",
                "28/02/2025;;LOYER;-500,00",
                "31/03/2025;;LOYER;-500,00",
                "30/04/2025;;LOYER;-500,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"));
    }

    @Test
    @DisplayName("Two identical one-off purchases not detected")
    void twoIdentical() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "01/01/2025;;MAGASIN;-20,00",
                "15/01/2025;;MAGASIN;-20,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Insufficient history not candidate subscription")
    void insufficient() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Salary excluded")
    void salaryExcluded() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "01/01/2025;;SALAIRE;2000,00",
                "01/02/2025;;SALAIRE;2000,00",
                "01/03/2025;;SALAIRE;2000,00",
                "01/04/2025;;SALAIRE;2000,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Internal transfer excluded")
    void transferExcluded() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        // transaction type determined by import? Amount negative with label VIREMENT may be transfer? Import classification sets TRANSFER if label contains VIREMENT?
        importCsv(user, account,
                "01/01/2025;;VIREMENT INTERNE;-100,00",
                "01/02/2025;;VIREMENT INTERNE;-100,00",
                "01/03/2025;;VIREMENT INTERNE;-100,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Refund excluded")
    void refundExcluded() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "01/01/2025;;REMBOURSEMENT;50,00",
                "01/02/2025;;REMBOURSEMENT;50,00",
                "01/03/2025;;REMBOURSEMENT;50,00");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Confirm and dismiss decisions preserved")
    void decisions() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String list = mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list);
        String id = arr.get(0).get("id").asText();

        mockMvc.perform(patch("/v1/recurring/" + id + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"seriesType\":\"SUBSCRIPTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // re-analyze should preserve confirmed
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Dismissed does not reappear without new reason")
    void dismissedNoReappear() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String list = mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list);
        String id = arr.get(0).get("id").asText();
        mockMvc.perform(patch("/v1/recurring/" + id + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());

        // re-analyze same data
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring/candidates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Repeated analysis no duplicates")
    void repeatedNoDuplicates() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("New transaction added to series")
    void newTransactionAdded() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // add new transaction via new import
        importCsv(user, account,
                "05/04/2025;;NETFLIX;-15,99");
        // need force? new occurrence should be added on analyze
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occurrenceCount").value(4));
    }

    @Test
    @DisplayName("Two users isolation")
    void twoUsers() throws Exception {
        User u1 = activeUser();
        User u2 = activeUser();
        Account a1 = createAccount(u1, "Compte");
        Account a2 = createAccount(u2, "Compte");
        importCsv(u1, a1,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        importCsv(u2, a2,
                "05/01/2025;;SPOTIFY;-9,99",
                "05/02/2025;;SPOTIFY;-9,99",
                "05/03/2025;;SPOTIFY;-9,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u1.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("NETFLIX"));
    }

    @Test
    @DisplayName("Cross access denied")
    void crossAccess() throws Exception {
        User u1 = activeUser();
        User u2 = activeUser();
        Account a1 = createAccount(u1, "Compte");
        importCsv(u1, a1,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u1.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String list = mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u1.getId())))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list);
        String id = arr.get(0).get("id").asText();

        mockMvc.perform(get("/v1/recurring/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/v1/recurring/" + id + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Foreign account forbidden")
    void foreignAccount() throws Exception {
        User u1 = activeUser();
        User u2 = activeUser();
        Account a1 = createAccount(u1, "Compte");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"" + a1.getId() + "\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Missing JWT unauthorized")
    void missingJwt() throws Exception {
        mockMvc.perform(get("/v1/recurring"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/recurring/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Detail includes reasons and occurrences")
    void detail() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        importCsv(user, account,
                "05/01/2025;;NETFLIX;-15,99",
                "05/02/2025;;NETFLIX;-15,99",
                "05/03/2025;;NETFLIX;-15,99");
        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String list = mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list);
        String id = arr.get(0).get("id").asText();
        mockMvc.perform(get("/v1/recurring/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons").isArray())
                .andExpect(jsonPath("$.occurrences.length()").value(3))
                .andExpect(jsonPath("$.nextEstimatedDate").exists())
                .andExpect(jsonPath("$.confidenceLevel").exists());
    }
}
