package com.eparly.api.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScalarDocsControllerTest {

    @Test
    @DisplayName("Scalar retourne le HTML de documentation lorsque OpenAPI est public en environnement local")
    void returnsHtmlWhenOpenApiIsPublicInLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        ScalarDocsController controller = new ScalarDocsController(env, true);
        ResponseEntity<String> response = controller.renderScalar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getBody())
                .contains("<title>Eparly API Reference | Scalar Documentation</title>")
                .contains("data-url=\"/v3/api-docs\"")
                .contains("cdn.jsdelivr.net/npm/@scalar/api-reference")
                .contains("Space Grotesk")
                .contains("\"theme\": \"purple\"");
    }

    @Test
    @DisplayName("Scalar retourne 404 lorsque OpenAPI n'est pas public")
    void returnsNotFoundWhenOpenApiIsNotPublic() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        ScalarDocsController controller = new ScalarDocsController(env, false);
        ResponseEntity<String> response = controller.renderScalar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Scalar retourne 404 lorsque le profil est prod, même si openApiPublic est true")
    void returnsNotFoundInProductionProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ScalarDocsController controller = new ScalarDocsController(env, true);
        ResponseEntity<String> response = controller.renderScalar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("MockMvc : /scalar et /docs répondent 200 avec headers et contenu Scalar")
    void mockMvcServesScalarAndDocsRoutes() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        ScalarDocsController controller = new ScalarDocsController(env, true);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/scalar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(content().string(containsString("data-url=\"/v3/api-docs\"")))
                .andExpect(content().string(containsString("cdn.jsdelivr.net/npm/@scalar/api-reference")));

        mockMvc.perform(get("/docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-url=\"/v3/api-docs\"")));
    }
}
