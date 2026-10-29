package com.eparly.api.auth;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import com.eparly.api.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class AuthIntegrationTest {
    private static final String PASSWORD = "Correct-Horse-Battery-42!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void resetMailMock() {
        reset(mailSender);
    }

    @Test
    void validRegistrationCreatesPendingUserSettingsAndSendsVerificationEmail() throws Exception {
        String email = uniqueEmail();
        var result = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created; check your email to verify it"))
                .andReturn();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getPasswordHash()).startsWith("$argon2id$").isNotEqualTo(PASSWORD);
        assertThat(userSettingsRepository.findByUserId(user.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consent_events WHERE user_id = ? AND consent_type = 'TERMS_OF_SERVICE' " +
                        "AND status = 'GRANTED' AND information_version = 'v1'",
                Long.class, user.getId()
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consent_events WHERE user_id = ? AND consent_type = 'MARKETING'",
                Long.class, user.getId()
        )).isEqualTo(0L);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD, user.getPasswordHash());
        SimpleMailMessage mail = capturedVerificationMail();
        assertThat(mail.getTo()).containsExactly(email.toLowerCase());
        String rawToken = tokenFrom(mail);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM email_verification_tokens WHERE user_id = ?",
                String.class, user.getId()
        );
        assertThat(storedHash).isEqualTo(sha256(rawToken)).isNotEqualTo(rawToken);
    }

    @Test
    void invalidEmailIsRejectedWithoutSendingEmail() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", true)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mailSender);
    }

    @Test
    void duplicateEmailWithDifferentCaseIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email.toUpperCase(), true)))
                .andExpect(status().isConflict());
    }

    @Test
    void verificationActivatesAccountAndTokenCannotBeReused() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = tokenFrom(capturedVerificationMail());

        mockMvc.perform(verifyRequest(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        mockMvc.perform(verifyRequest(token)).andExpect(status().isBadRequest());
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = tokenFrom(capturedVerificationMail());
        jdbcTemplate.update(
                "UPDATE email_verification_tokens SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 day', " +
                        "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE token_hash = ?",
                sha256(token)
        );

        mockMvc.perform(verifyRequest(token)).andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorizedResponse() throws Exception {
        User user = activeUser(uniqueEmail());

        mockMvc.perform(loginRequest(user.getEmail(), "incorrect-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginBeforeEmailVerificationIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(loginRequest(email, PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void suspendedUserCannotLogin() throws Exception {
        User user = new User(uniqueEmail(), passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(loginRequest(user.getEmail(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void activeUserReceivesShortLivedBearerAccessToken() throws Exception {
        User user = activeUser(uniqueEmail());

        String body = mockMvc.perform(loginRequest(user.getEmail(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        String accessToken = response.get("accessToken").asText();
        String refreshToken = response.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).hasSize(43);
        assertThat(response.get("refreshExpiresIn").asLong()).isEqualTo(2_592_000);
        assertThat(body).doesNotContain(PASSWORD, user.getPasswordHash());
        String storedTokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_token_sessions WHERE user_id = ?",
                String.class, user.getId()
        );
        assertThat(storedTokenHash).isEqualTo(sha256(refreshToken)).isNotEqualTo(refreshToken);
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void refreshRotatesTokenAndConsumesThePreviousGeneration() throws Exception {
        User user = activeUser(uniqueEmail());
        JsonNode login = loginTokens(user);
        String previousToken = login.get("refreshToken").asText();
        String previousHash = sha256(previousToken);

        JsonNode rotated = refreshTokens(previousToken);

        assertThat(rotated.get("accessToken").asText()).isNotBlank();
        assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(previousToken);
        assertThat(rotated.get("refreshExpiresIn").asLong()).isBetween(1L, 2_592_000L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM refresh_token_sessions WHERE token_hash = ?",
                Boolean.class, previousHash
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT replaced_by_session_id IS NOT NULL FROM refresh_token_sessions WHERE token_hash = ?",
                Boolean.class, previousHash
        )).isTrue();

        mockMvc.perform(refreshRequest(previousToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void reusedRefreshTokenRevokesEveryTokenInItsFamily() throws Exception {
        User user = activeUser(uniqueEmail());
        String originalToken = loginTokens(user).get("refreshToken").asText();
        String independentSessionToken = loginTokens(user).get("refreshToken").asText();
        String rotatedToken = refreshTokens(originalToken).get("refreshToken").asText();

        mockMvc.perform(refreshRequest(originalToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(refreshRequest(rotatedToken)).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE family_id = " +
                        "(SELECT family_id FROM refresh_token_sessions WHERE token_hash = ?) " +
                        "AND revoked_reason = 'TOKEN_REUSE'",
                Long.class, sha256(originalToken)
        )).isEqualTo(2L);
        assertThat(refreshTokens(independentSessionToken).get("refreshToken").asText())
                .isNotEqualTo(independentSessionToken);
    }

    @Test
    void expiredRefreshTokenIsRejectedAndMarkedExpired() throws Exception {
        User user = activeUser(uniqueEmail());
        String refreshToken = loginTokens(user).get("refreshToken").asText();
        String tokenHash = sha256(refreshToken);
        jdbcTemplate.update(
                "UPDATE refresh_token_sessions SET created_at = CURRENT_TIMESTAMP - INTERVAL '31 days', " +
                        "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE token_hash = ?",
                tokenHash
        );

        mockMvc.perform(refreshRequest(refreshToken)).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_reason FROM refresh_token_sessions WHERE token_hash = ?",
                String.class, tokenHash
        )).isEqualTo("EXPIRED");
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(refreshRequest("A".repeat(43))).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsIdempotentAndRevokesTheWholeSessionFamily() throws Exception {
        User user = activeUser(uniqueEmail());
        JsonNode login = loginTokens(user);
        String originalToken = login.get("refreshToken").asText();
        String rotatedToken = refreshTokens(originalToken).get("refreshToken").asText();
        String accessToken = login.get("accessToken").asText();

        mockMvc.perform(logoutRequest(rotatedToken)).andExpect(status().isNoContent());
        mockMvc.perform(logoutRequest(rotatedToken)).andExpect(status().isNoContent());
        mockMvc.perform(refreshRequest(rotatedToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(refreshRequest(originalToken)).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE family_id = " +
                        "(SELECT family_id FROM refresh_token_sessions WHERE token_hash = ?) " +
                        "AND revoked_reason = 'LOGOUT'",
                Long.class, sha256(originalToken)
        )).isEqualTo(2L);
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void logoutOfUnknownTokenIsStillSuccessful() throws Exception {
        mockMvc.perform(logoutRequest("B".repeat(43))).andExpect(status().isNoContent());
    }

    @Test
    void refreshIsRejectedAndFamilyRevokedWhenUserIsSuspended() throws Exception {
        User user = activeUser(uniqueEmail());
        String refreshToken = loginTokens(user).get("refreshToken").asText();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(refreshRequest(refreshToken)).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_reason FROM refresh_token_sessions WHERE token_hash = ?",
                String.class, sha256(refreshToken)
        )).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void concurrentRefreshRequestsCannotCreateTwoActiveTokens() throws Exception {
        User user = activeUser(uniqueEmail());
        String refreshToken = loginTokens(user).get("refreshToken").asText();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(refreshRequest(refreshToken)).andReturn();
            });
            Future<MvcResult> second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(refreshRequest(refreshToken)).andReturn();
            });
            start.countDown();

            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();
            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 401);

            String familyQuery = "SELECT COUNT(*) FROM refresh_token_sessions WHERE family_id = " +
                    "(SELECT family_id FROM refresh_token_sessions WHERE token_hash = ?) " +
                    "AND consumed_at IS NULL AND revoked_at IS NULL";
            assertThat(jdbcTemplate.queryForObject(familyQuery, Long.class, sha256(refreshToken))).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void privateProfileWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void privateProfileWithInvalidJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateProfileWithExpiredJwtReturnsUnauthorized() throws Exception {
        User user = activeUser(uniqueEmail());
        String expiredJwt = jwtFor(user.getId(), Instant.now().minusSeconds(1200), Instant.now().minusSeconds(60));

        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateProfileRejectsJwtWithWrongIssuerOrAudience() throws Exception {
        User user = activeUser(uniqueEmail());
        String wrongIssuer = jwtFor(user.getId(), "untrusted-issuer",
                List.of("eparly-test-client"), Instant.now().minusSeconds(1), Instant.now().plusSeconds(900));
        String wrongAudience = jwtFor(user.getId(), "eparly-test",
                List.of("another-client"), Instant.now().minusSeconds(1), Instant.now().plusSeconds(900));

        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanReadOnlyTheProfileBoundToTheirToken() throws Exception {
        User owner = activeUser(uniqueEmail());
        User other = activeUser(uniqueEmail());
        String token = jwtFor(owner.getId(), Instant.now().minusSeconds(1), Instant.now().plusSeconds(900));

        String body = mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(other.getEmail(), "passwordHash", "password_hash", "verificationToken");
    }

    @Test
    void suspendedUserCannotReadTheirProfileEvenWithAnUnexpiredJwt() throws Exception {
        User user = new User(uniqueEmail(), passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(user);
        String token = jwtFor(user.getId(), Instant.now().minusSeconds(1), Instant.now().plusSeconds(900));

        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void verificationAndProfileResponsesDoNotExposePasswordHashesOrInternalTokens() throws Exception {
        String email = uniqueEmail();
        register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String verificationToken = tokenFrom(capturedVerificationMail());

        String verificationBody = mockMvc.perform(verifyRequest(verificationToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String profileBody = mockMvc.perform(get("/v1/me")
                        .header("Authorization", "Bearer " + jwtFor(
                                user.getId(), Instant.now().minusSeconds(1), Instant.now().plusSeconds(900))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(verificationBody).doesNotContain(verificationToken, user.getPasswordHash(), "password");
        assertThat(profileBody).doesNotContain(verificationToken, user.getPasswordHash(), "password");
    }

    @Test
    void resendVerificationAlwaysReturnsNeutralMessage() throws Exception {
        mockMvc.perform(post("/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If the account can be verified, a message will be sent"));
    }

    @Test
    void resendDeliveryFailureRemainsNeutralAndRollsBackTokenRotation() throws Exception {
        String email = uniqueEmail();
        register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        Long tokenCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = ?", Long.class, user.getId()
        );
        reset(mailSender);
        doThrow(new MailSendException("simulated mail outage"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If the account can be verified, a message will be sent"));

        Long tokenCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = ?", Long.class, user.getId()
        );
        assertThat(tokenCountAfter).isEqualTo(tokenCountBefore);
    }

    @Test
    void registrationMailFailureReturnsServiceUnavailableAndRollsBackAccount() throws Exception {
        String email = uniqueEmail();
        doThrow(new MailSendException("simulated mail outage"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, true)))
                .andExpect(status().isServiceUnavailable());

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void actuatorHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, true)))
                .andExpect(status().isCreated());
    }

    private JsonNode loginTokens(User user) throws Exception {
        String response = mockMvc.perform(loginRequest(user.getEmail(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode refreshTokens(String refreshToken) throws Exception {
        String response = mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private User activeUser(String email) {
        User user = new User(email, passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private String registerBody(String email, boolean termsAccepted) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"," +
                "\"termsAccepted\":" + termsAccepted + ",\"marketingConsent\":false}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password
    ) {
        return post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verifyRequest(String token) {
        return post("/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(String token) {
        return post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder logoutRequest(String token) {
        return post("/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}");
    }

    private SimpleMailMessage capturedVerificationMail() {
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static String tokenFrom(SimpleMailMessage message) {
        Matcher matcher = Pattern.compile("(?m)^([0-9]{6})$").matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String jwtFor(UUID userId, Instant issuedAt, Instant expiresAt) {
        return jwtFor(userId, "eparly-test", List.of("eparly-test-client"), issuedAt, expiresAt);
    }

    private String jwtFor(
            UUID userId,
            String issuer,
            List<String> audiences,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(audiences)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims
        )).getTokenValue();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
