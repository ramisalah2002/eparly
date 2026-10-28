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
class SubscriptionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;

    private User activeUser() {
        User user = new User("sub-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(UUID userId) {
        return ImportTestSupport.bearer(jwtEncoder, userId);
    }

    private Account createAccount(User user, String name) throws Exception {
        return ImportTestSupport.createAccount(mockMvc, jwtEncoder, objectMapper, user, name);
    }

    @Test
    @DisplayName("Manual subscription creation")
    void manualCreate() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "accountId", account.getId().toString(),
                "name", "Spotify",
                "provider", "Spotify AB",
                "declaredAmountMinor", 999,
                "currency", "EUR",
                "declaredFrequency", "MONTHLY",
                "declaredStatus", "ACTIVE"
        ));

        mockMvc.perform(post("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spotify"))
                .andExpect(jsonPath("$.origin").value("USER_DECLARED"))
                .andExpect(jsonPath("$.monthlyEquivalentMinor").value(999.0000))
                .andExpect(jsonPath("$.annualEquivalentMinor").value(11988.0000));

        mockMvc.perform(get("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Update declared amount and frequency")
    void update() throws Exception {
        User user = activeUser();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Netflix",
                "declaredAmountMinor", 1599,
                "currency", "EUR",
                "declaredFrequency", "MONTHLY"
        ));
        String resp = mockMvc.perform(post("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(resp).get("id").asText();

        String patch = objectMapper.writeValueAsString(java.util.Map.of(
                "declaredAmountMinor", 1799,
                "declaredFrequency", "MONTHLY",
                "notes", "augmentation"
        ));
        mockMvc.perform(patch("/v1/subscriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredAmountMinor").value(1799))
                .andExpect(jsonPath("$.monthlyEquivalentMinor").value(1799.0000));
    }

    @Test
    @DisplayName("Status lifecycle and summary")
    void statusAndSummary() throws Exception {
        User user = activeUser();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Gym",
                "declaredAmountMinor", 3000,
                "currency", "EUR",
                "declaredFrequency", "MONTHLY",
                "declaredStatus", "ACTIVE"
        ));
        String resp = mockMvc.perform(post("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(resp).get("id").asText();

        mockMvc.perform(patch("/v1/subscriptions/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\",\"note\":\"resilie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredStatus").value("CANCELED"));

        mockMvc.perform(get("/v1/subscriptions/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubscriptions").value(1));
    }

    @Test
    @DisplayName("Isolation between users")
    void isolation() throws Exception {
        User u1 = activeUser();
        User u2 = activeUser();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "Secret",
                "declaredAmountMinor", 1000,
                "currency", "EUR",
                "declaredFrequency", "MONTHLY"
        ));
        String resp = mockMvc.perform(post("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u1.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(resp).get("id").asText();

        mockMvc.perform(get("/v1/subscriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Transform detected recurrence")
    void transform() throws Exception {
        User user = activeUser();
        Account account = createAccount(user, "Compte");
        // import CSV
        StringBuilder csv = new StringBuilder("Date operation;Date valeur;Libell\u00e9;Montant\n");
        csv.append("05/01/2025;;NETFLIX;-15,99\n");
        csv.append("05/02/2025;;NETFLIX;-15,99\n");
        csv.append("05/03/2025;;NETFLIX;-15,99\n");
        csv.append("05/04/2025;;NETFLIX;-15,99\n");
        byte[] content = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile("file", "r.csv", "text/csv", content);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/imports")
                        .file(file)
                        .param("accountId", account.getId().toString())
                        .param("mapping", ImportTestSupport.MAPPING_FR)
                        .param("fileHash", hash)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/recurring/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String list = mockMvc.perform(get("/v1/recurring")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andReturn().getResponse().getContentAsString();
        String seriesId = objectMapper.readTree(list).get(0).get("id").asText();

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "recurringSeriesId", seriesId,
                "name", "Netflix",
                "declaredStatus", "ACTIVE"
        ));
        mockMvc.perform(post("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("AUTO_DETECTED"))
                .andExpect(jsonPath("$.recurringSeriesId").value(seriesId))
                .andExpect(jsonPath("$.observedUsualAmountMinor").value(1599));
    }
}
