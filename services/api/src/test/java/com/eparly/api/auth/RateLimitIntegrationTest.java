package com.eparly.api.auth;

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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
@TestPropertySource(properties = {
        "eparly.security.rate-limit.login-account-max-attempts=3",
        "eparly.security.rate-limit.login-ip-max-attempts=5",
        "eparly.security.rate-limit.forgot-password-email-max-attempts=2",
        "eparly.security.rate-limit.forgot-password-ip-max-attempts=5",
        "eparly.security.rate-limit.resend-verification-email-max-attempts=2",
        "eparly.security.rate-limit.resend-verification-ip-max-attempts=5",
        "eparly.security.rate-limit.register-ip-max-attempts=3",
        "eparly.security.rate-limit.use-forwarded-for=false"
})
class RateLimitIntegrationTest {

    private static final String PASSWORD = "ValidPassword2026!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("12, 14. Rate limiting sur login après échecs répétés retourne HTTP 429 et Retry-After")
    void loginRateLimitingAfterExcessiveFailuresReturns429WithRetryAfter() throws Exception {
        String testIp = "192.0.2." + (10 + (int) (Math.random() * 200));
        String email = uniqueEmail();
        createActiveUser(email, PASSWORD);

        // 3 tentatives infructueuses (seuil configuré = 3)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // La 4ème tentative est bloquée en 429 avec Retry-After
        mockMvc.perform(post("/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }

    @Test
    @DisplayName("13, 14. Protection contre les demandes excessives d'emails (forgot-password)")
    void forgotPasswordRateLimitingReturns429WithRetryAfter() throws Exception {
        String testIp = "198.51.100." + (10 + (int) (Math.random() * 200));
        String email = uniqueEmail();
        createActiveUser(email, PASSWORD);

        // 2 demandes autorisées (seuil configuré = 2)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/auth/forgot-password")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isOk());
        }

        // La 3ème demande est bloquée en 429
        mockMvc.perform(post("/v1/auth/forgot-password")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    @DisplayName("13. Protection contre les demandes excessives sur resend-verification")
    void resendVerificationRateLimitingReturns429() throws Exception {
        String testIp = "203.0.113." + (10 + (int) (Math.random() * 200));
        String email = uniqueEmail();

        // 2 demandes autorisées
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/auth/resend-verification")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isOk());
        }

        // 3ème demande bloquée
        mockMvc.perform(post("/v1/auth/resend-verification")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Ignorer les en-têtes X-Forwarded-For non fiables pour empêcher le contournement d'IP")
    void untrustedXForwardedForHeadersAreIgnored() throws Exception {
        String realIp = "192.0.2.77";
        String email = uniqueEmail();
        createActiveUser(email, PASSWORD);

        // Bloquer par l'IP réelle
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(realIp);
                                return request;
                            })
                            .header("X-Forwarded-For", "1.1.1." + i) // tentative d'usurpation d'IP spoofing
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // La tentative suivante avec un autre X-Forwarded-For bidon doit toujours être bloquée (IP réelle retenue)
        mockMvc.perform(post("/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(realIp);
                            return request;
                        })
                        .header("X-Forwarded-For", "9.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private User createActiveUser(String email, String password) {
        User user = new User(email, passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private static String uniqueEmail() {
        return "ratelimit-" + UUID.randomUUID() + "@eparly.test";
    }
}
