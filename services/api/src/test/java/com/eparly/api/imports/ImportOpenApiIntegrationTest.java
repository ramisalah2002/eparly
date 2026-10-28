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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.eparly.api.imports.ImportTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contrat OpenAPI genere doit exposer les endpoints d'import, le format multipart, les codes HTTP et des exemples.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class ImportOpenApiIntegrationTest {

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

    @Test
    @DisplayName("OpenAPI : endpoints d'import, multipart, codes HTTP, exemples JSON et pagination documentes")
    void openApiDescribesTheImportContract() throws Exception {
        User created = new User("oas-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        created.setStatus(UserStatus.ACTIVE);
        User user = userRepository.saveAndFlush(created);

        String body = mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        JsonNode paths = root.get("paths");

        assertThat(paths.has("/v1/imports/preview")).isTrue();
        assertThat(paths.has("/v1/imports")).isTrue();
        assertThat(paths.has("/v1/imports/{id}")).isTrue();
        assertThat(paths.has("/v1/accounts/{accountId}/imports")).isTrue();

        JsonNode preview = paths.get("/v1/imports/preview").get("post");
        JsonNode previewMultipart = preview.get("requestBody").get("content").get("multipart/form-data");
        assertThat(previewMultipart).isNotNull();
        assertThat(resolve(root, previewMultipart.get("schema")).toString())
                .contains("accountId", "file", "mapping", "charset", "delimiter", "binary");
        assertThat(preview.get("responses").has("200")).isTrue();
        assertThat(preview.get("responses").has("413")).isTrue();
        assertThat(preview.get("responses").get("200").toString()).contains("fileHash", "wouldInsert", "AMBIGUOUS_IDENTICAL_ROWS");

        JsonNode importPost = paths.get("/v1/imports").get("post");
        JsonNode importMultipart = importPost.get("requestBody").get("content").get("multipart/form-data");
        assertThat(resolve(root, importMultipart.get("schema")).toString())
                .contains("accountId", "file", "mapping", "fileHash", "charset", "delimiter", "binary");
        assertThat(preview.has("parameters") && preview.get("parameters").toString().contains("accountId"))
                .as("les champs multipart ne doivent pas etre decrits comme parametres de requete").isFalse();
        for (String code : new String[]{"201", "400", "401", "403", "404", "409", "413", "415", "422"}) {
            assertThat(importPost.get("responses").has(code)).as("code HTTP " + code).isTrue();
        }
        assertThat(importPost.get("responses").get("201").toString()).contains("ImportResultResponse", "examples");
        assertThat(importPost.get("responses").get("201").get("content").get("application/json").get("examples").toString())
                .contains("insertedRows", "skippedRows");
        assertThat(importPost.get("responses").get("422").toString()).contains("importJobId", "INVALID_DATE");
        assertThat(importPost.get("responses").get("409").toString()).contains("FILE_HASH_MISMATCH");

        String list = paths.get("/v1/imports").get("get").toString();
        assertThat(list).contains("page", "size", "totalElements");
        assertThat(root.get("components").get("schemas").has("ImportMappingDto")).isTrue();
        assertThat(root.get("components").get("schemas").get("ImportPreviewResponse").toString())
                .contains("fileHash", "wouldInsert", "wouldSkip", "charsetAmbiguous");
        assertThat(paths.get("/v1/accounts/{accountId}/imports").get("get").toString()).contains("page", "size");
    }

    private static JsonNode resolve(JsonNode root, JsonNode schema) {
        if (schema.has("$ref")) {
            String name = schema.get("$ref").asText().substring(schema.get("$ref").asText().lastIndexOf('/') + 1);
            return root.get("components").get("schemas").get(name);
        }
        return schema;
    }
}
