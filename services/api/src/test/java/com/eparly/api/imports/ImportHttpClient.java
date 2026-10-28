package com.eparly.api.imports;

import com.eparly.api.user.domain.User;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Client HTTP de test (MockMvc) pour les parcours preview / import.
 */
final class ImportHttpClient {

    private final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;
    private final ObjectMapper objectMapper;

    ImportHttpClient(MockMvc mockMvc, JwtEncoder jwtEncoder, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
        this.objectMapper = objectMapper;
    }

    ResultActions preview(User user, UUID accountId, byte[] csv, String mapping) throws Exception {
        return preview(ImportTestSupport.bearer(jwtEncoder, user.getId()), accountId, csv, mapping);
    }

    ResultActions preview(String authorization, UUID accountId, byte[] csv, String mapping) throws Exception {
        MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/v1/imports/preview");
        request.file(new MockMultipartFile("file", "releve.csv", "text/csv", csv));
        request.param("accountId", accountId.toString());
        if (mapping != null) {
            request.param("mapping", mapping);
        }
        request.header(HttpHeaders.AUTHORIZATION, authorization);
        return mockMvc.perform(request);
    }

    ResultActions importCsv(User user, UUID accountId, byte[] csv, String mapping) throws Exception {
        return importCsv(ImportTestSupport.bearer(jwtEncoder, user.getId()), accountId, csv, mapping);
    }

    ResultActions importCsv(String authorization, UUID accountId, byte[] csv, String mapping) throws Exception {
        MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/v1/imports");
        request.file(new MockMultipartFile("file", "releve.csv", "text/csv", csv));
        request.param("accountId", accountId.toString());
        request.param("mapping", mapping);
        request.param("fileHash", sha256(csv));
        request.header(HttpHeaders.AUTHORIZATION, authorization);
        return mockMvc.perform(request);
    }

    /**
     * Import attendu en 201 ; retourne l'objet importJob de la reponse.
     */
    JsonNode importOk(User user, UUID accountId, byte[] csv, String mapping) throws Exception {
        return importOk(ImportTestSupport.bearer(jwtEncoder, user.getId()), accountId, csv, mapping);
    }

    JsonNode importOk(String authorization, UUID accountId, byte[] csv, String mapping) throws Exception {
        String body = importCsv(authorization, accountId, csv, mapping)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("importJob");
    }

    static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
