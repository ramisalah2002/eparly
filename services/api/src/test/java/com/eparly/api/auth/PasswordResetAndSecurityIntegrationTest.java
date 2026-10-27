package com.eparly.api.auth;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.auth.domain.PasswordResetToken;
import com.eparly.api.auth.dto.LoginRequest;
import com.eparly.api.auth.dto.ResetPasswordRequest;
import com.eparly.api.auth.repository.EmailVerificationTokenRepository;
import com.eparly.api.auth.repository.PasswordResetTokenRepository;
import com.eparly.api.auth.repository.RefreshTokenSessionRepository;
import com.eparly.api.auth.service.AccessTokenService;
import com.eparly.api.auth.service.AuthCleanupService;
import com.eparly.api.auth.service.AuthService;
import com.eparly.api.user.domain.ConsentEvent;
import com.eparly.api.user.domain.ConsentStatus;
import com.eparly.api.user.domain.ConsentType;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.ConsentEventRepository;
import com.eparly.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
class PasswordResetAndSecurityIntegrationTest {

    private static final String INITIAL_PASSWORD = "InitialValidPassword2026!";
    private static final String NEW_PASSWORD = "BrandNewValidPassword2026!";

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
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Autowired
    private ConsentEventRepository consentEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthCleanupService authCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        reset(mailSender);
    }

    // -------------------------------------------------------------------------
    // Exigences 1, 2, 3 : Demandes de récupération, neutralité et email
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1 & 3. Demande de récupération pour compte existant génère un token hashé et expédie l'email")
    void forgotPasswordForExistingAccountSendsEmailAndStoresHashOnly() throws Exception {
        String email = uniqueEmail();
        User user = createActiveUser(email, INITIAL_PASSWORD);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists and can receive emails, a reset message has been sent"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();

        assertThat(mail.getTo()).containsExactly(email);
        assertThat(mail.getSubject()).contains("Réinitialisation");
        String rawToken = tokenFrom(mail);
        assertThat(rawToken).hasSize(43);
        assertThat(mail.getText()).doesNotContain(INITIAL_PASSWORD, NEW_PASSWORD);
        assertThat(mail.getText()).contains("http://localhost/test-reset-password");

        String tokenHash = sha256(rawToken);
        var tokenInDb = passwordResetTokenRepository.findByTokenHash(tokenHash);
        assertThat(tokenInDb).isPresent();
        assertThat(tokenInDb.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(tokenInDb.get().isUsed()).isFalse();
        assertThat(tokenInDb.get().getExpiresAt()).isAfter(Instant.now());

        // L'utilisateur n'est pas modifié avant validation du token
        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, refreshedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("2. Demande pour compte inexistant retourne la même réponse neutre sans envoyer d'email")
    void forgotPasswordForNonExistentAccountReturnsNeutralResponse() throws Exception {
        String email = "unknown-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists and can receive emails, a reset message has been sent"));

        verifyNoInteractions(mailSender);
    }

    // -------------------------------------------------------------------------
    // Exigences 4, 8, 9 : Réinitialisation avec token valide, nouveau et ancien mot de passe
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4, 8, 9. Réinitialisation avec token valide applique le nouveau mot de passe et rejette l'ancien")
    void resetPasswordWithValidTokenSucceedsAndUpdatesPassword() throws Exception {
        String email = uniqueEmail();
        User user = createActiveUser(email, INITIAL_PASSWORD);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String token = tokenFrom(captor.getValue());
        reset(mailSender);

        // Validation de la réinitialisation
        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));

        // Email de confirmation sans mot de passe
        ArgumentCaptor<SimpleMailMessage> confirmCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(confirmCaptor.capture());
        assertThat(confirmCaptor.getValue().getSubject()).contains("Confirmation");
        assertThat(confirmCaptor.getValue().getText()).doesNotContain(NEW_PASSWORD, INITIAL_PASSWORD);

        // Le token est marqué comme utilisé
        var tokenInDb = passwordResetTokenRepository.findByTokenHash(sha256(token));
        assertThat(tokenInDb).isPresent();
        assertThat(tokenInDb.get().isUsed()).isTrue();

        // Ancien mot de passe refusé
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // Nouveau mot de passe accepté
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // Exigences 5, 6 : Token expiré et token déjà consommé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("5. Jeton expiré est rejeté avec HTTP 400")
    void resetPasswordWithExpiredTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        User user = createActiveUser(email, INITIAL_PASSWORD);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String token = tokenFrom(captor.getValue());

        // Forcer l'expiration dans la base de données tout en respectant chk_prt_expires_after_created
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours', expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE token_hash = ?",
                sha256(token)
        );

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password reset token is invalid or expired"));

        // Le mot de passe initial reste inchangé
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, refreshed.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("6. Jeton déjà consommé est rejeté avec HTTP 400")
    void resetPasswordWithAlreadyUsedTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        createActiveUser(email, INITIAL_PASSWORD);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String token = tokenFrom(captor.getValue());

        // Première utilisation réussie
        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // Deuxième tentative avec le même jeton rejetée
        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password reset token is invalid or expired"));
    }

    // -------------------------------------------------------------------------
    // Exigence 7 : Concurrence atomique sur le même token
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7. Deux demandes concurrentes avec le même jeton ne peuvent aboutir qu'à un seul succès")
    void concurrentResetPasswordRequestsSerializedAtomically() throws Exception {
        String email = uniqueEmail();
        createActiveUser(email, INITIAL_PASSWORD);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String token = tokenFrom(captor.getValue());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task1 = () -> {
            startLatch.await();
            return mockMvc.perform(post("/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "1\"}"))
                    .andReturn().getResponse().getStatus();
        };

        Callable<Integer> task2 = () -> {
            startLatch.await();
            return mockMvc.perform(post("/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "2\"}"))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);
        startLatch.countDown();

        int status1 = f1.get();
        int status2 = f2.get();
        executor.shutdown();

        List<Integer> statuses = List.of(status1, status2);
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
    }

    // -------------------------------------------------------------------------
    // Exigences 10 & 11 : Révocation des sessions de renouvellement et invalidation immédiate des JWT
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("10 & 11. Réinitialisation révoque tous les refresh tokens et invalide immédiatement tous les anciens JWT")
    void passwordResetRevokesAllRefreshTokensAndInvalidatesOldJwts() throws Exception {
        String email = uniqueEmail();
        User user = createActiveUser(email, INITIAL_PASSWORD);

        // Connexion initiale : obtention d'un access token et d'un refresh token
        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String oldAccessToken = json.get("accessToken").asText();
        String oldRefreshToken = json.get("refreshToken").asText();

        // Le JWT permet d'accéder au profil avant réinitialisation
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isOk());

        // Demande et exécution de la réinitialisation
        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        String resetToken = tokenFrom(captor.getValue());

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 10. L'ancien refresh token est désormais révoqué (motif PASSWORD_RESET)
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        String revokedReason = jdbcTemplate.queryForObject(
                "SELECT revoked_reason FROM refresh_token_sessions WHERE token_hash = ?",
                String.class,
                sha256(oldRefreshToken)
        );
        assertThat(revokedReason).isEqualTo("PASSWORD_RESET");

        // 11. L'ancien access token JWT est immédiatement refusé par Spring Security
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());

        // Une nouvelle connexion émet un nouveau JWT pleinement fonctionnel
        MvcResult newLoginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = objectMapper.readTree(newLoginResult.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/v1/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Exigence 15 : Droits et statuts utilisateurs (compte SUSPENDED)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("15. Un utilisateur suspendu ne peut ni réinitialiser son mot de passe ni se connecter")
    void suspendedUserCannotResetPasswordOrLogin() throws Exception {
        String email = uniqueEmail();
        User user = createActiveUser(email, INITIAL_PASSWORD);

        // Générer un token valide
        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String token = tokenFrom(captor.getValue());

        // Suspendre l'utilisateur
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        // La réinitialisation échoue
        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        // La connexion échoue
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Exigence 16 : Absence de secrets dans les représentations / logs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("16. Absence de mot de passe et de token brut dans User.toString et les réponses API")
    void secretsAreNotExposedInToStringOrResponses() throws Exception {
        User user = new User("secret.user@eparly.test", "$argon2id$v=19$m=65536,t=3,p=1$secretHashValue");
        String str = user.toString();

        assertThat(str).doesNotContain("secretHashValue");
        assertThat(str).doesNotContain("secret.user@eparly.test");

        String email = uniqueEmail();
        createActiveUser(email, INITIAL_PASSWORD);

        MvcResult result = mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(INITIAL_PASSWORD, "token", "password");
    }

    // -------------------------------------------------------------------------
    // Nettoyage automatique : purge des données expirées sans perte RGPD ni utilisateurs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Purge automatique des données expirées en conservant les comptes et les consentements RGPD")
    void cleanupTaskPurgesExpiredDataPreservingUsersAndConsents() {
        String email = uniqueEmail();
        User user = createActiveUser(email, INITIAL_PASSWORD);

        // Ajouter un événement de consentement RGPD
        ConsentEvent consent = new ConsentEvent(user, ConsentType.TERMS_OF_SERVICE, "v1", ConsentStatus.GRANTED);
        consentEventRepository.saveAndFlush(consent);

        // Insérer un jeton de récupération expiré au-delà de la rétention (8 jours) en respectant la contrainte chk_prt_expires_after_created
        UUID expiredId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (id, user_id, token_hash, created_at, expires_at) " +
                        "VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '8 days')",
                expiredId,
                user.getId(),
                "expired-hash-" + UUID.randomUUID()
        );

        // Exécuter le nettoyage
        AuthCleanupService.CleanupReport report = authCleanupService.cleanupAll();
        assertThat(report.deletedResetTokens()).isGreaterThanOrEqualTo(1);

        // L'utilisateur et le consentement RGPD existent toujours intacts
        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(consentEventRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).isNotEmpty();
        assertThat(passwordResetTokenRepository.findById(expiredId)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Méthodes utilitaires
    // -------------------------------------------------------------------------

    private User createActiveUser(String email, String password) {
        User user = new User(email, passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@eparly.test";
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String tokenFrom(SimpleMailMessage message) {
        Matcher matcher = Pattern.compile("(?m)^([A-Za-z0-9_-]{43})$").matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
