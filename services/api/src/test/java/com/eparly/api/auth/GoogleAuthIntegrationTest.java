package com.eparly.api.auth;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.auth.config.GoogleAuthProperties;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.AuthCleanupService;
import com.eparly.api.auth.service.GoogleChallengeService;
import com.eparly.api.auth.service.GoogleIdTokenVerifier;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les jetons sont signes par une cle de test et verifies par le vrai code, via un serveur JWKS local. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GoogleAuthIntegrationTest {
    private static final String CLIENT_ID = "test-web-client.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String PASSWORD = "Correct-Horse-Battery-42!";
    private static final RSAKey KEY = generateKey("test-key-1");
    private static final RSAKey ROTATED_KEY = generateKey("test-key-2");
    private static final RSAKey FOREIGN_KEY = generateKey("test-key-1");
    private static final AtomicReference<String> JWKS = new AtomicReference<>(new JWKSet(KEY.toPublicJWK()).toString());
    private static final HttpServer SERVER = startServer();

    @FunctionalInterface
    private interface TokenFor {
        String apply(String nonce);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void google(DynamicPropertyRegistry registry) {
        registry.add("eparly.auth.google.enabled", () -> "true");
        registry.add("eparly.auth.google.allowed-client-ids", () -> CLIENT_ID);
        registry.add("eparly.auth.google.jwk-set-uri", GoogleAuthIntegrationTest::jwksUrl);
        registry.add("eparly.auth.google.jwk-refresh-min-interval", () -> "PT0.001S");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthCleanupService cleanupService;
    @MockitoBean private JavaMailSender mailSender;

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void resetKeys() {
        JWKS.set(new JWKSet(KEY.toPublicJWK()).toString());
    }

    // --- Verification du jeton (1 a 7) ---

    @Test
    void invalidIdentitiesAreRejectedWithoutCreatingAnything() throws Exception {
        long users = userRepository.count();
        String sub = uniqueSubject();
        List<TokenFor> invalid = List.of(
                token(FOREIGN_KEY, sub, c -> { }),
                token(KEY, sub, c -> c.issuer("https://evil.example.com")),
                token(KEY, sub, c -> c.audience("other-client")),
                token(KEY, sub, c -> c.claim("azp", "other-client")),
                token(KEY, sub, c -> c.expirationTime(Date.from(Instant.now().minus(Duration.ofHours(1))))),
                token(KEY, sub, c -> c.claim("email_verified", false)),
                token(KEY, sub, c -> c.claim("email", null)),
                token(KEY, null, c -> { }),
                nonce -> unsigned(sub),
                nonce -> hmacSigned(sub),
                nonce -> "not-a-jwt",
                nonce -> "ya29.opaque-google-access-token");
        for (TokenFor token : invalid) {
            login(token, true).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(GoogleIdTokenVerifier.INVALID_IDENTITY));
        }
        assertThat(userRepository.count()).isEqualTo(users);
    }

    @Test
    void keyRotationIsPickedUpAndAcceptedIssuerVariantWorks() throws Exception {
        JWKS.set(new JWKSet(List.of(KEY.toPublicJWK(), ROTATED_KEY.toPublicJWK())).toString());
        Thread.sleep(5);
        login(token(ROTATED_KEY, uniqueSubject(), c -> c.issuer("accounts.google.com")), true)
                .andExpect(status().isOk());
    }

    @Test
    void disabledOrUnconfiguredVerifierIsNeverPermissive() {
        Duration d = Duration.ofSeconds(1);
        String token = signed(KEY, claims(uniqueSubject(), c -> { }));
        for (var properties : List.of(
                new GoogleAuthProperties(false, List.of(CLIENT_ID), jwksUrl(), d, d, d, Duration.ofMinutes(5)),
                new GoogleAuthProperties(true, List.of(" "), jwksUrl(), d, d, d, Duration.ofMinutes(5)))) {
            assertThatThrownBy(() -> new GoogleIdTokenVerifier(properties).verify(token))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus().value()).isEqualTo(503));
        }
    }

    @Test
    void unreachableGoogleKeysReturnServiceUnavailableNotUnauthorized() {
        Duration d = Duration.ofSeconds(1);
        var verifier = new GoogleIdTokenVerifier(new GoogleAuthProperties(true, List.of(CLIENT_ID),
                "http://127.0.0.1:1/certs", d, d, d, Duration.ofMinutes(5)));
        assertThatThrownBy(() -> verifier.verify(signed(KEY, claims(uniqueSubject(), c -> { }))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));
    }

    // --- Comptes (8 a 10, 13, 20) ---

    @Test
    void newUserWithoutConsentGetsNothingThenConsentCreatesPasswordlessActiveAccount() throws Exception {
        String sub = uniqueSubject();
        String email = sub + "@gmail.test";
        TokenFor token = token(KEY, sub, c -> c.claim("name", "Alex Martin"));

        login(token, false).andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.message").value("GOOGLE_CONSENT_REQUIRED"));
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();

        JsonNode session = json(login(token, true).andExpect(status().isOk()));
        assertThat(session.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(session.path("expiresIn").asLong()).isEqualTo(900);

        var row = jdbc.queryForMap("SELECT id, password_hash, status, email_verified_at, full_name FROM users WHERE email = ?", email);
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("email_verified_at")).isNotNull();
        assertThat(row.get("full_name")).isEqualTo("Alex Martin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_settings WHERE user_id = ?", Integer.class, row.get("id"))).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT consent_type FROM consent_events WHERE user_id = ?", String.class, row.get("id")))
                .containsExactly("TERMS_OF_SERVICE");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_oauth_identities
                WHERE provider = 'GOOGLE' AND provider_subject = ? AND email_at_link_time = ?
                """, Integer.class, sub, email)).isEqualTo(1);

        // Sans mot de passe local : la connexion par mot de passe echoue.
        postJson("/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void linkedAccountLogsInWithoutConsentAndChangedEmailDoesNotMatter() throws Exception {
        String sub = uniqueSubject();
        login(token(KEY, sub, c -> { }), true).andExpect(status().isOk());
        long users = userRepository.count();

        login(token(KEY, sub, c -> c.claim("email", "changed-" + sub + "@gmail.test")), false)
                .andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(users);
    }

    @Test
    void existingClassicEmailIsNeverMergedAutomatically() throws Exception {
        String sub = uniqueSubject();
        User classic = classicUser(sub + "@gmail.test", UserStatus.ACTIVE);

        login(token(KEY, sub, c -> c.claim("email", classic.getEmail().toUpperCase())), true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("GOOGLE_ACCOUNT_LINK_REQUIRED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_identities WHERE user_id = ?", Integer.class, classic.getId())).isZero();
    }

    @Test
    void disabledLinkedAccountIsRefused() throws Exception {
        String sub = uniqueSubject();
        TokenFor token = token(KEY, sub, c -> { });
        login(token, true).andExpect(status().isOk());
        for (String state : List.of("SUSPENDED", "DELETED")) {
            jdbc.update("UPDATE users SET status = ? WHERE email = ?", state, sub + "@gmail.test");
            login(token, true).andExpect(status().isForbidden());
        }
    }

    // --- Liaison (11, 12) ---

    @Test
    void linkRequiresSessionThenLetsBothLoginMethodsWorkAndIsIdempotent() throws Exception {
        String sub = uniqueSubject();
        String email = "classic-" + sub + "@example.test";
        classicUser(email, UserStatus.ACTIVE);
        TokenFor token = token(KEY, sub, c -> { });

        postJson("/v1/auth/google/link", "{\"idToken\":\"x\",\"challengeId\":\"" + UUID.randomUUID() + "\"}", null).andExpect(status().isUnauthorized());

        String access = passwordLogin(email).path("accessToken").asText();
        link(token, access).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.alreadyLinked").value(false));
        link(token, access).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyLinked").value(true));

        JsonNode googleSession = json(login(token, false).andExpect(status().isOk()));
        mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + googleSession.path("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        passwordLogin(email);
    }

    @Test
    void googleIdentityCannotBeLinkedToTwoAccountsNorTwoGoogleAccountsToOne() throws Exception {
        String first = "first-" + UUID.randomUUID() + "@example.test";
        String second = "second-" + UUID.randomUUID() + "@example.test";
        classicUser(first, UserStatus.ACTIVE);
        classicUser(second, UserStatus.ACTIVE);
        TokenFor token = token(KEY, uniqueSubject(), c -> { });
        String firstAccess = passwordLogin(first).path("accessToken").asText();

        link(token, firstAccess).andExpect(status().isOk());
        link(token, passwordLogin(second).path("accessToken").asText())
                .andExpect(status().isConflict());
        link(token(KEY, uniqueSubject(), c -> { }), firstAccess)
                .andExpect(status().isConflict());
    }

    @Test
    void linkWithoutValidCurrentPasswordIsRefused() throws Exception {
        String email = "proof-" + UUID.randomUUID() + "@example.test";
        classicUser(email, UserStatus.ACTIVE);
        TokenFor token = token(KEY, uniqueSubject(), c -> { });
        String access = passwordLogin(email).path("accessToken").asText();

        linkWithPassword(token, access, null).andExpect(status().isUnauthorized());
        linkWithPassword(token, access, "wrong-password")
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_identities oi JOIN users u ON u.id = oi.user_id WHERE u.email = ?",
                Integer.class, email)).isZero();
    }

    // --- Sessions (14 a 18) ---

    @Test
    void googleSessionUsesEparlyJwtRefreshRotationReuseDetectionAndLogout() throws Exception {
        JsonNode session = json(login(token(KEY, uniqueSubject(), c -> { }), true).andExpect(status().isOk()));
        String access = session.path("accessToken").asText();
        JsonNode jwt = objectMapper.readTree(Base64.getUrlDecoder().decode(access.split("\\.")[1]));
        assertThat(jwt.path("iss").asText()).isEqualTo("eparly-test");
        assertThat(jwt.has("email_verified")).isFalse();
        mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)).andExpect(status().isOk());

        String firstRefresh = session.path("refreshToken").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token_sessions WHERE token_hash = encode(sha256(?::bytea), 'hex')",
                Integer.class, firstRefresh)).isEqualTo(1);
        JsonNode rotated = json(postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + firstRefresh + "\"}", null).andExpect(status().isOk()));
        String secondRefresh = rotated.path("refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        postJson("/v1/auth/logout", "{\"refreshToken\":\"" + secondRefresh + "\"}", null).andExpect(status().isNoContent());
        postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + secondRefresh + "\"}", null).andExpect(status().isUnauthorized());

        JsonNode reuse = json(login(token(KEY, uniqueSubject(), c -> { }), true).andExpect(status().isOk()));
        String old = reuse.path("refreshToken").asText();
        String next = json(postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + old + "\"}", null)).path("refreshToken").asText();
        postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + old + "\"}", null).andExpect(status().isUnauthorized());
        postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + next + "\"}", null).andExpect(status().isUnauthorized());
    }

    @Test
    void googleNeverGrantsCatalogAdmin() throws Exception {
        String sub = uniqueSubject();
        TokenFor token = token(KEY, sub, c -> c.claim("scope", "CATALOG_ADMIN").claim("roles", List.of("CATALOG_ADMIN")));
        JsonNode challenge = challenge("LOGIN", null);
        String request = "{\"idToken\":\"" + token.apply(challenge.path("nonce").asText()) + "\",\"challengeId\":\"" + challenge.path("challengeId").asText()
                + "\",\"termsAccepted\":true,\"scope\":\"CATALOG_ADMIN\",\"authorities\":[\"CATALOG_ADMIN\"]}";

        String access = json(postJson("/v1/auth/google", request, null).andExpect(status().isOk())).path("accessToken").asText();

        mockMvc.perform(get("/v1/admin/catalog/offers").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_admin_permissions p JOIN users u ON u.id = p.user_id WHERE u.email = ?
                """, Integer.class, sub + "@gmail.test")).isZero();
    }

    // --- Concurrence (19) ---

    @Test
    void concurrentFirstLoginsCreateExactlyOneAccount() throws Exception {
        String sub = uniqueSubject();
        TokenFor token = token(KEY, sub, c -> { });

        List<Integer> statuses = concurrently(8, () -> login(token, true).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, sub + "@gmail.test")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_identities WHERE provider_subject = ?", Integer.class, sub)).isEqualTo(1);
    }

    @Test
    void concurrentLinksOfOneIdentityHaveASingleWinner() throws Exception {
        String sub = uniqueSubject();
        TokenFor token = token(KEY, sub, c -> { });
        List<String> access = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String email = "race-" + UUID.randomUUID() + "@example.test";
            classicUser(email, UserStatus.ACTIVE);
            access.add(passwordLogin(email).path("accessToken").asText());
        }
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();

        List<Integer> statuses = concurrently(4, () -> link(token, access.get(next.getAndIncrement())).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses.stream().filter(code -> code == 200)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_identities WHERE provider_subject = ?", Integer.class, sub)).isEqualTo(1);
    }

    @Test
    void uniquenessIsEnforcedByPostgresAndNoGoogleTokenIsStored() {
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint WHERE conrelid = 'user_oauth_identities'::regclass AND contype = 'u'
                """, String.class)).contains("uq_user_oauth_provider_subject", "uq_user_oauth_user_provider");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'user_oauth_identities'
                """, String.class)).noneMatch(column -> column.contains("token"));
    }

    // --- Challenge et anti-rejeu ---

    private static final String CHALLENGE_INVALID = GoogleChallengeService.CHALLENGE_INVALID;

    @Test
    void challengesAreUniqueAndOnlyTheirHashIsStored() throws Exception {
        JsonNode first = challenge("LOGIN", null);
        JsonNode second = challenge("LOGIN", null);
        String nonce = first.path("nonce").asText();
        assertThat(nonce).isNotEqualTo(second.path("nonce").asText()).hasSize(43);
        assertThat(first.path("purpose").asText()).isEqualTo("LOGIN");
        assertThat(Instant.parse(first.path("expiresAt").asText()))
                .isBetween(Instant.now().plus(Duration.ofMinutes(4)), Instant.now().plus(Duration.ofMinutes(6)));

        var row = jdbc.queryForMap("SELECT nonce_hash, user_id, consumed_at FROM google_auth_challenges WHERE id = ?::uuid", first.path("challengeId").asText());
        assertThat(row.get("nonce_hash").toString()).hasSize(64).isNotEqualTo(nonce);
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("consumed_at")).isNull();
        postJson("/v1/auth/google/challenge", "{\"purpose\":\"OTHER\"}", null).andExpect(status().isBadRequest());
    }

    @Test
    void unknownExpiredOrAlreadyConsumedChallengeIsRefused() throws Exception {
        TokenFor token = token(KEY, uniqueSubject(), c -> { });
        submitLogin(token.apply("x"), UUID.randomUUID().toString(), true).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));

        JsonNode expired = challenge("LOGIN", null);
        jdbc.update("UPDATE google_auth_challenges SET created_at = now() - interval '10 minutes', expires_at = now() - interval '1 second' WHERE id = ?::uuid",
                expired.path("challengeId").asText());
        submitLogin(token.apply(expired.path("nonce").asText()), expired.path("challengeId").asText(), true)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));
    }

    @Test
    void sameTokenAndChallengeCannotBeReplayedAfterSuccess() throws Exception {
        TokenFor token = token(KEY, uniqueSubject(), c -> { });
        JsonNode challenge = challenge("LOGIN", null);
        String nonce = challenge.path("nonce").asText();
        String id = challenge.path("challengeId").asText();
        String idToken = token.apply(nonce);

        JsonNode session = json(submitLogin(idToken, id, true).andExpect(status().isOk()));
        assertThat(session.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM google_auth_challenges WHERE id = ?::uuid", Boolean.class, id)).isTrue();

        submitLogin(idToken, id, true).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));
        // Le meme jeton avec un nouveau challenge porte l'ancien nonce : refuse aussi.
        submitLogin(idToken, challenge("LOGIN", null).path("challengeId").asText(), true)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(GoogleIdTokenVerifier.INVALID_IDENTITY));
    }

    @Test
    void tokenWithoutNonceOrWithWrongNonceIsRefusedAndKeepsChallengeUsable() throws Exception {
        String sub = uniqueSubject();
        JsonNode challenge = challenge("LOGIN", null);
        String id = challenge.path("challengeId").asText();

        submitLogin(signed(KEY, claims(sub, c -> { })), id, true).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GoogleIdTokenVerifier.INVALID_IDENTITY));
        submitLogin(token(KEY, sub, c -> { }).apply("wrong-nonce"), id, true).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GoogleIdTokenVerifier.INVALID_IDENTITY));
        assertThat(userRepository.findByEmailIgnoreCase(sub + "@gmail.test")).isEmpty();

        // Un tiers qui connait l'identifiant ne peut pas griller le challenge avec un jeton invalide.
        submitLogin(token(KEY, sub, c -> { }).apply(challenge.path("nonce").asText()), id, true).andExpect(status().isOk());
    }

    @Test
    void consentRetryCanReuseTheUnconsumedChallengeOnlyUntilItSucceeds() throws Exception {
        String sub = uniqueSubject();
        JsonNode challenge = challenge("LOGIN", null);
        String id = challenge.path("challengeId").asText();
        String idToken = token(KEY, sub, c -> { }).apply(challenge.path("nonce").asText());

        submitLogin(idToken, id, false).andExpect(status().isPreconditionRequired());
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL FROM google_auth_challenges WHERE id = ?::uuid", Boolean.class, id)).isTrue();
        submitLogin(idToken, id, true).andExpect(status().isOk());
        submitLogin(idToken, id, true).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentUseOfOneChallengeHasASingleWinner() throws Exception {
        String sub = uniqueSubject();
        JsonNode challenge = challenge("LOGIN", null);
        String id = challenge.path("challengeId").asText();
        String idToken = token(KEY, sub, c -> { }).apply(challenge.path("nonce").asText());

        List<Integer> statuses = concurrently(8, () -> submitLogin(idToken, id, true).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200, 401);
        assertThat(statuses.stream().filter(code -> code == 200)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, sub + "@gmail.test")).isEqualTo(1);
    }

    @Test
    void challengePurposeAndOwnerAreEnforced() throws Exception {
        String sub = uniqueSubject();
        TokenFor token = token(KEY, sub, c -> { });
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.test";
        String otherEmail = "other-" + UUID.randomUUID() + "@example.test";
        String ownerAccess = activeUserAccess(ownerEmail).path("accessToken").asText();
        String otherAccess = activeUserAccess(otherEmail).path("accessToken").asText();

        postJson("/v1/auth/google/challenge", "{\"purpose\":\"LINK\"}", null).andExpect(status().isUnauthorized());

        JsonNode login = challenge("LOGIN", ownerAccess);
        submitLink(token.apply(login.path("nonce").asText()), login.path("challengeId").asText(), ownerAccess, PASSWORD)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));

        JsonNode owned = challenge("LINK", ownerAccess);
        String ownedId = owned.path("challengeId").asText();
        String idToken = token.apply(owned.path("nonce").asText());
        assertThat(jdbc.queryForObject("SELECT u.email FROM google_auth_challenges c JOIN users u ON u.id = c.user_id WHERE c.id = ?::uuid",
                String.class, ownedId)).isEqualTo(ownerEmail);
        submitLogin(idToken, ownedId, true).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));
        submitLink(idToken, ownedId, otherAccess, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_identities WHERE provider_subject = ?", Integer.class, sub)).isZero();

        submitLink(idToken, ownedId, ownerAccess, PASSWORD).andExpect(status().isOk());
        submitLink(idToken, ownedId, ownerAccess, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(CHALLENGE_INVALID));
    }

    @Test
    void challengeCreationIsRateLimitedAndExpiredChallengesAreCleanedUp() throws Exception {
        String ip = "198.51.100.77";
        jdbc.update("""
                INSERT INTO rate_limit_entries (entry_key, attempt_count, window_start, expires_at)
                VALUES (?, 1000, now(), now() + interval '1 minute')
                """, "google-challenge:ip:" + ip);
        mockMvc.perform(post("/v1/auth/google/challenge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"LOGIN\"}").with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests());

        String expired = challenge("LOGIN", null).path("challengeId").asText();
        String active = challenge("LOGIN", null).path("challengeId").asText();
        jdbc.update("UPDATE google_auth_challenges SET created_at = now() - interval '10 minutes', expires_at = now() - interval '1 minute' WHERE id = ?::uuid", expired);

        assertThat(cleanupService.cleanupAll().deletedGoogleChallenges()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_auth_challenges WHERE id = ?::uuid", Integer.class, expired)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM google_auth_challenges WHERE id = ?::uuid", Integer.class, active)).isEqualTo(1);
    }

    @Test
    void databaseRefusesAnInconsistentChallengeOwner() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO google_auth_challenges (id, nonce_hash, purpose, user_id, created_at, expires_at)
                VALUES (gen_random_uuid(), ?, 'LINK', NULL, now(), now() + interval '5 minutes')
                """, UUID.randomUUID().toString())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // --- Outils ---

    private List<Integer> concurrently(int count, Callable<Integer> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) results.add(future.get());
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions login(TokenFor token, boolean terms) throws Exception {
        JsonNode challenge = challenge("LOGIN", null);
        return submitLogin(token.apply(challenge.path("nonce").asText()), challenge.path("challengeId").asText(), terms);
    }

    private org.springframework.test.web.servlet.ResultActions submitLogin(String idToken, String challengeId, boolean terms) throws Exception {
        return postJson("/v1/auth/google", "{\"idToken\":\"" + idToken + "\",\"challengeId\":\"" + challengeId
                + "\",\"termsAccepted\":" + terms + "}", null);
    }

    private org.springframework.test.web.servlet.ResultActions link(TokenFor token, String access) throws Exception {
        return linkWithPassword(token, access, PASSWORD);
    }

    private org.springframework.test.web.servlet.ResultActions linkWithPassword(TokenFor token, String access, String password) throws Exception {
        JsonNode challenge = challenge("LINK", access);
        return submitLink(token.apply(challenge.path("nonce").asText()), challenge.path("challengeId").asText(), access, password);
    }

    private org.springframework.test.web.servlet.ResultActions submitLink(String idToken, String challengeId, String access, String password) throws Exception {
        return postJson("/v1/auth/google/link", "{\"idToken\":\"" + idToken + "\",\"challengeId\":\"" + challengeId + "\""
                + (password == null ? "" : ",\"currentPassword\":\"" + password + "\"") + "}", access);
    }

    private JsonNode challenge(String purpose, String bearer) throws Exception {
        return json(postJson("/v1/auth/google/challenge", "{\"purpose\":\"" + purpose + "\"}", bearer).andExpect(status().isOk()));
    }

    private JsonNode activeUserAccess(String email) throws Exception {
        classicUser(email, UserStatus.ACTIVE);
        return passwordLogin(email);
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body, String bearer) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        return mockMvc.perform(request);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode passwordLogin(String email) throws Exception {
        return json(postJson("/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null)
                .andExpect(status().isOk()));
    }

    private User classicUser(String email, UserStatus state) {
        User user = new User(email, passwordEncoder.encode(PASSWORD));
        user.setStatus(state);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private static String uniqueSubject() {
        return "1" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static JWTClaimsSet claims(String sub, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(ISSUER).audience(CLIENT_ID)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .claim("email", sub + "@gmail.test").claim("email_verified", true);
        if (sub != null) builder.subject(sub);
        customizer.accept(builder);
        return builder.build();
    }

    /** Le jeton est signe une fois le nonce connu, comme Google apres la demande du SDK. */
    private static TokenFor token(RSAKey key, String sub, Consumer<JWTClaimsSet.Builder> customizer) {
        return nonce -> signed(key, claims(sub, c -> {
            c.claim("nonce", nonce);
            customizer.accept(c);
        }));
    }

    private static String signed(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String unsigned(String sub) {
        return new com.nimbusds.jwt.PlainJWT(claims(sub, c -> { })).serialize();
    }

    private static String hmacSigned(String sub) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(sub, c -> { }));
            jwt.sign(new MACSigner("0123456789012345678901234567890123456789".getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey generateKey(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String jwksUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/certs";
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/certs", exchange -> {
                byte[] body = JWKS.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
