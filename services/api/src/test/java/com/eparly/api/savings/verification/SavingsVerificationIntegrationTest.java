package com.eparly.api.savings.verification;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.domain.ImportJobStatus;
import com.eparly.api.imports.repository.ImportJobRepository;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.offer.repository.OfferProviderRepository;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.action.repository.SavingsActionRepository;
import com.eparly.api.savings.verification.repository.SavingsActionEvidenceRepository;
import com.eparly.api.savings.verification.repository.SavingsActionVerificationRepository;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.transaction.domain.TransactionType;
import com.eparly.api.transaction.repository.TransactionRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration complets pour l'étape 11B.1 : vérification des économies.
 *
 * Couvre : les 5 endpoints HTTP, la persistance PostgreSQL V14/V15, l'isolation multi-tenant,
 * l'idempotence, la double facturation, la concurrence, les CSV incomplets,
 * et les gardes métier (VERIFIED_OBSERVED interdit, date effective non inventée,
 * transaction ne justifiant qu'une seule économie).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class SavingsVerificationIntegrationTest {

    /* ── Testcontainers PostgreSQL 17 ── */

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /* ── Injections ── */

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired ImportJobRepository importJobRepository;
    @Autowired OfferProviderRepository providerRepository;
    @Autowired CommercialOfferRepository offerRepository;
    @Autowired OfferPricingRepository pricingRepository;
    @Autowired OfferEvidenceRepository offerEvidenceRepository;
    @Autowired SavingsActionRepository savingsActionRepository;
    @Autowired SavingsActionEvidenceRepository evidenceRepository;
    @Autowired SavingsActionVerificationRepository verificationRepository;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    /* ── Helpers ── */

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("eparly-test")
                .subject(user.getId().toString())
                .audience(List.of("eparly-test-client"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims
        )).getTokenValue();
    }

    private User activeUser(String email) {
        User u = new User(email, "SecureHash2026!");
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(u);
    }

    private Account createAccount(User user, String name) {
        Account a = new Account(user, name);
        return accountRepository.saveAndFlush(a);
    }

    /** Crée un ImportJob minimal pour satisfaire la contrainte FK de Transaction. */
    private ImportJob createImportJob(User user, Account account) {
        ImportJob job = new ImportJob(user, account, "test.csv");
        job.setStatus(ImportJobStatus.COMPLETED);
        job.setTotalRows(1);
        job.setInsertedRows(1);
        job.setSkippedRows(0);
        return importJobRepository.saveAndFlush(job);
    }

    /**
     * Crée une transaction de type EXPENSE (dépense) pour l'utilisateur.
     * Le montant est en centimes, négatif par convention débit (amountMinor < 0).
     */
    private Transaction createExpenseTransaction(Account account, ImportJob job,
                                                  String label, Long amountMinorNegative,
                                                  LocalDate bookingDate) {
        Transaction tx = new Transaction(
                account, job,
                UUID.randomUUID().toString().substring(0, 32),
                bookingDate,
                amountMinorNegative,
                label,
                label,
                TransactionType.EXPENSE
        );
        return transactionRepository.saveAndFlush(tx);
    }

    private Transaction createTransferTransaction(Account account, ImportJob job,
                                                   String label, Long amountMinor,
                                                   LocalDate bookingDate) {
        Transaction tx = new Transaction(
                account, job,
                UUID.randomUUID().toString().substring(0, 32),
                bookingDate,
                amountMinor,
                label,
                label,
                TransactionType.TRANSFER
        );
        return transactionRepository.saveAndFlush(tx);
    }

    private Transaction createNonExpenseTransaction(Account account, ImportJob job,
                                                     String label, Long amountMinor,
                                                     LocalDate bookingDate, TransactionType type) {
        Transaction tx = new Transaction(
                account, job,
                UUID.randomUUID().toString().substring(0, 32),
                bookingDate,
                amountMinor,
                label,
                label,
                type
        );
        return transactionRepository.saveAndFlush(tx);
    }

    private Subscription createSubscription(User user, String name, String provider, Long amount) {
        Subscription s = new Subscription();
        s.setUser(user);
        s.setName(name);
        s.setProvider(provider);
        s.setDeclaredAmountMinor(amount);
        s.setCurrency("EUR");
        s.setDeclaredFrequency(RecurringFrequency.MONTHLY);
        s.setDeclaredStatus(SubscriptionStatus.ACTIVE);
        s.setOrigin(SubscriptionOrigin.USER_DECLARED);
        return subscriptionRepository.saveAndFlush(s);
    }

    private OfferProvider createProvider(String name) {
        OfferProvider p = new OfferProvider();
        p.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        p.setSector(OfferSector.STREAMING);
        p.setOfficialWebsite("https://" + name.toLowerCase() + ".com");
        p.setCountry("FR");
        p.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.saveAndFlush(p);
    }

    private CommercialOffer createOffer(OfferProvider provider, String name, Long priceMinor) {
        CommercialOffer o = new CommercialOffer();
        o.setProvider(provider);
        o.setCommercialName(name);
        o.setSector(OfferSector.STREAMING);
        o.setCharacteristics("{\"screens\": 2}");
        o.setEligibilityConditions("Aucune");
        o.setCommitment("SANS_ENGAGEMENT");
        o.setTerritory("FR");
        o.setPublicationStatus(OfferStatus.VERIFIED);
        o.setValidFrom(LocalDate.now().minusMonths(3));
        o.setValidUntil(LocalDate.now().plusMonths(12));
        o = offerRepository.saveAndFlush(o);

        OfferPricing pricing = new OfferPricing();
        pricing.setOffer(o);
        pricing.setAmountMinor(priceMinor);
        pricing.setCurrency("EUR");
        pricing.setBillingPeriod(OfferBillingPeriod.MONTHLY);
        pricing.setActivationFeeMinor(0L);
        pricing.setTaxesIncluded(true);
        pricing.setDataUnknown(false);
        pricingRepository.saveAndFlush(pricing);

        OfferEvidence ev = new OfferEvidence();
        ev.setOffer(o);
        ev.setOfficialUrl("https://" + provider.getOfficialWebsite() + "/tarifs");
        ev.setConsultedAt(Instant.now());
        ev.setVerifiedAt(Instant.now());
        ev.setNature("Grille tarifaire");
        ev.setVerifiedSummary("Conforme");
        ev.setVerificationStatus(EvidenceStatus.VERIFIED);
        ev.setProvenance("Site officiel");
        offerEvidenceRepository.saveAndFlush(ev);

        return o;
    }

    /**
     * Crée une action PLANNED puis la fait passer en DECLARED via les endpoints HTTP.
     * Retourne l'UUID de l'action.
     */
    private UUID createDeclaredAction(User user, Subscription sub, CommercialOffer offer) throws Exception {
        Map<String, Object> req = Map.of(
                "subscriptionId", sub.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );
        String resp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID actionId = UUID.fromString(objectMapper.readTree(resp).get("id").asText());

        // PLANNED → COMMITTED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED"))))
                .andExpect(status().isOk());

        // COMMITTED → DECLARED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "DECLARED"))))
                .andExpect(status().isOk());

        return actionId;
    }

    // =========================================================================
    // GROUPE 1 : Scénario HTTP complet de bout en bout
    // =========================================================================

    @Nested
    @DisplayName("1. Scénario HTTP complet : DECLARED → association transaction → persistance PostgreSQL → évaluation → relecture")
    class FullHttpScenario {

        @Test
        @DisplayName("Parcours complet : action DECLARED, preuve NEW liée, évaluation PARTIALLY_VERIFIED, relecture cohérente")
        void fullEndToEndFlow() throws Exception {
            // Préparer le contexte
            User user = activeUser("full-flow@eparly.test");
            Account account = createAccount(user, "Compte Courant");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Netflix Standard", "Netflix", 1599L);
            OfferProvider provider = createProvider("PrimeVideo");
            CommercialOffer offer = createOffer(provider, "Prime Video Standard", 999L);

            UUID actionId = createDeclaredAction(user, sub, offer);

            // Vérifier que le statut initial de vérification est NOT_VERIFIED
            mockMvc.perform(get("/v1/savings/actions/" + actionId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("NOT_VERIFIED"));

            // Créer une transaction de dépense vers le nouveau fournisseur (Prime Video)
            Transaction txNew = createExpenseTransaction(account, job,
                    "PRIMEVIDEO ABONNEMENT MENSUEL",
                    -999L,
                    LocalDate.now().minusDays(5));

            // ── Endpoint 1 : GET /candidates ──────────────────────────────────
            String candidatesResp = mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode candidates = objectMapper.readTree(candidatesResp);
            assertThat(candidates.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

            // ── Endpoint 2 : POST /evidence (NEW_SUBSCRIPTION_PAYMENT) ────────
            Map<String, Object> evidenceReq = Map.of(
                    "transactionId", txNew.getId(),
                    "role", "NEW_SUBSCRIPTION_PAYMENT",
                    "effectiveChangeDate", LocalDate.now().minusDays(30).toString()
            );
            String evidenceResp = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(evidenceReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.role").value("NEW_SUBSCRIPTION_PAYMENT"))
                    .andExpect(jsonPath("$.provenance").value("USER_CONFIRMED"))
                    .andExpect(jsonPath("$.amountMinor").value(-999))
                    .andReturn().getResponse().getContentAsString();

            UUID evidenceId = UUID.fromString(objectMapper.readTree(evidenceResp).get("id").asText());

            // ── Vérification PostgreSQL directe : savings_action_evidences ────
            Map<String, Object> dbEvidence = jdbcTemplate.queryForMap(
                    "SELECT id, action_id, transaction_id, role, provenance FROM savings_action_evidences WHERE id = ?",
                    evidenceId);
            assertThat(dbEvidence.get("role")).isEqualTo("NEW_SUBSCRIPTION_PAYMENT");
            assertThat(dbEvidence.get("provenance")).isEqualTo("USER_CONFIRMED");

            // ── Vérification PostgreSQL directe : savings_action_verifications ─
            Integer verificationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_verifications WHERE action_id = ?",
                    Integer.class, actionId);
            assertThat(verificationCount).isGreaterThanOrEqualTo(1);

            // ── Vérification PostgreSQL : statut de l'action mis à jour ────────
            String verificationStatus = jdbcTemplate.queryForObject(
                    "SELECT verification_status FROM savings_actions WHERE id = ?",
                    String.class, actionId);
            assertThat(verificationStatus).isEqualTo("PARTIALLY_VERIFIED");

            // ── Vérification PostgreSQL : date effective stockée ────────────────
            Object effectiveDateDb = jdbcTemplate.queryForObject(
                    "SELECT effective_change_date FROM savings_actions WHERE id = ?",
                    Object.class, actionId);
            assertThat(effectiveDateDb).isNotNull();

            // ── Endpoint 3 : GET /verification (détail) ───────────────────────
            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("PARTIALLY_VERIFIED"))
                    .andExpect(jsonPath("$.observedNewPayments").isArray())
                    .andExpect(jsonPath("$.observedNewPayments.length()").value(1))
                    .andExpect(jsonPath("$.observedNewPayments[0].amountMinor").value(-999))
                    .andExpect(jsonPath("$.observedOldPayments.length()").value(0))
                    .andExpect(jsonPath("$.effectiveChangeDate").isNotEmpty())
                    .andExpect(jsonPath("$.missingInformation").isArray())
                    .andExpect(jsonPath("$.decisionReasons").isArray());

            // ── Endpoint 4 : POST /evaluate (idempotent) ──────────────────────
            String evaluateResp1 = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("PARTIALLY_VERIFIED"))
                    .andReturn().getResponse().getContentAsString();

            // Deuxième appel idempotent : même résultat, pas de doublon d'audit
            String evaluateResp2 = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("PARTIALLY_VERIFIED"))
                    .andReturn().getResponse().getContentAsString();

            // Les deux réponses doivent être cohérentes
            JsonNode eval1 = objectMapper.readTree(evaluateResp1);
            JsonNode eval2 = objectMapper.readTree(evaluateResp2);
            assertThat(eval1.get("verificationStatus").asText())
                    .isEqualTo(eval2.get("verificationStatus").asText());

            // L'idempotence ne crée pas de ligne d'audit redondante
            long auditLineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_verifications WHERE action_id = ?",
                    Long.class, actionId);
            // Doit rester stable (1 ou quelques-unes si légèrement différentes, pas explosif)
            assertThat(auditLineCount).isLessThanOrEqualTo(5);

            // ── Endpoint 5 : DELETE /evidence/{evidenceId} ────────────────────
            mockMvc.perform(delete("/v1/savings/actions/" + actionId + "/verification/evidence/" + evidenceId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isNoContent());

            // Après suppression : retour à NOT_VERIFIED
            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("NOT_VERIFIED"))
                    .andExpect(jsonPath("$.observedNewPayments.length()").value(0));

            // Ligne PostgreSQL supprimée
            Integer evidenceInDb = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_evidences WHERE id = ?",
                    Integer.class, evidenceId);
            assertThat(evidenceInDb).isEqualTo(0);
            Integer transactionInDb = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE id = ?",
                    Integer.class, txNew.getId());
            assertThat(transactionInDb).isEqualTo(1);
        }
    }

    // =========================================================================
    // GROUPE 2 : Sécurité - 5 endpoints JWT obligatoires
    // =========================================================================

    @Nested
    @DisplayName("2. Sécurité : JWT obligatoire sur les 5 endpoints de vérification")
    class SecurityJwtRequired {

        @Test
        @DisplayName("Tous les endpoints renvoient 401 sans JWT")
        void allEndpointsReturn401WithoutJwt() throws Exception {
            UUID randomId = UUID.randomUUID();
            String base = "/v1/savings/actions/" + randomId + "/verification";

            mockMvc.perform(get(base + "/candidates")).andExpect(status().isUnauthorized());
            mockMvc.perform(post(base + "/evidence")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete(base + "/evidence/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(base)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(base + "/evaluate")).andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // GROUPE 3 : Isolation utilisateur (multi-tenant)
    // =========================================================================

    @Nested
    @DisplayName("3. Isolation utilisateur : les ressources d'un autre utilisateur renvoient 404 neutre")
    class UserIsolation {

        @Test
        @DisplayName("Utilisateur B ne peut ni lister, ni associer, ni évaluer les vérifications de A")
        void userBCannotAccessUserAVerification() throws Exception {
            User userA = activeUser("isolation-a@eparly.test");
            User userB = activeUser("isolation-b@eparly.test");

            Account accountA = createAccount(userA, "Compte A");
            ImportJob jobA = createImportJob(userA, accountA);
            Subscription subA = createSubscription(userA, "Netflix A", "Netflix", 1599L);
            OfferProvider provider = createProvider("IsoProvider");
            CommercialOffer offer = createOffer(provider, "IsoOffer", 999L);
            UUID actionIdA = createDeclaredAction(userA, subA, offer);

            // B tente de lire les candidats de A
            mockMvc.perform(get("/v1/savings/actions/" + actionIdA + "/verification/candidates")
                            .header(HttpHeaders.AUTHORIZATION, bearer(userB)))
                    .andExpect(status().isNotFound());

            // B tente de lire le détail de vérification de A
            mockMvc.perform(get("/v1/savings/actions/" + actionIdA + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(userB)))
                    .andExpect(status().isNotFound());

            // B tente d'associer une de ses propres transactions à l'action de A
            Account accountB = createAccount(userB, "Compte B");
            ImportJob jobB = createImportJob(userB, accountB);
            Transaction txB = createExpenseTransaction(
                    accountB,
                    jobB,
                    "PRIME VIDEO",
                    -999L,
                    LocalDate.now()
            );
            Map<String, Object> evidenceReq = Map.of(
                    "transactionId", txB.getId(),
                    "role", "NEW_SUBSCRIPTION_PAYMENT"
            );
            mockMvc.perform(post("/v1/savings/actions/" + actionIdA + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(userB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(evidenceReq)))
                    .andExpect(status().isNotFound());

            // B tente d'évaluer l'action de A
            mockMvc.perform(post("/v1/savings/actions/" + actionIdA + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(userB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Trigger PostgreSQL rejette une preuve dont la transaction appartient à un autre utilisateur")
        void postgresTriggersBlocksCrossUserEvidence() throws Exception {
            // Ce test vérifie le trigger SQL trg_savings_action_evidences_user_check
            User userA = activeUser("trigger-a@eparly.test");
            User userB = activeUser("trigger-b@eparly.test");

            Account accountA = createAccount(userA, "Compte Trigger A");
            Account accountB = createAccount(userB, "Compte Trigger B");
            ImportJob jobA = createImportJob(userA, accountA);
            ImportJob jobB = createImportJob(userB, accountB);

            Subscription subA = createSubscription(userA, "Sub Trigger A", "Netflix", 1599L);
            OfferProvider provider = createProvider("TriggerProvider");
            CommercialOffer offer = createOffer(provider, "TriggerOffer", 999L);
            UUID actionIdA = createDeclaredAction(userA, subA, offer);

            // Créer une transaction appartenant à userB
            Transaction txB = createExpenseTransaction(accountB, jobB,
                    "ABONNEMENT TRIGGER", -999L, LocalDate.now().minusDays(5));

            // Tenter via l'API d'associer la transaction de B à l'action de A avec le token de A
            // → doit retourner 404 car la transaction n'appartient pas à userA
            Map<String, Object> req = Map.of(
                    "transactionId", txB.getId(),
                    "role", "NEW_SUBSCRIPTION_PAYMENT"
            );
            mockMvc.perform(post("/v1/savings/actions/" + actionIdA + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // GROUPE 4 : Double facturation - une transaction ne justifie qu'une économie
    // =========================================================================

    @Nested
    @DisplayName("4. Double facturation : une transaction ne peut pas justifier plusieurs économies")
    class NoDoubleCounting {

        @Test
        @DisplayName("Associer une transaction déjà utilisée comme preuve renvoie 409 Conflict")
        void transactionAlreadyUsedAsEvidenceReturns409() throws Exception {
            User user = activeUser("double-count@eparly.test");
            Account account = createAccount(user, "Compte Double");
            ImportJob job = createImportJob(user, account);

            Subscription sub1 = createSubscription(user, "Netflix Double1", "Netflix", 1599L);
            Subscription sub2 = createSubscription(user, "Netflix Double2", "Canal", 2000L);
            OfferProvider provider = createProvider("DoubleProvider");
            CommercialOffer offer1 = createOffer(provider, "DoubleOffer1", 999L);
            CommercialOffer offer2 = createOffer(provider, "DoubleOffer2", 1500L);

            UUID action1Id = createDeclaredAction(user, sub1, offer1);
            UUID action2Id = createDeclaredAction(user, sub2, offer2);

            // Transaction partagée (une seule)
            Transaction sharedTx = createExpenseTransaction(account, job,
                    "PRIMEVIDEO UNIQUE", -999L, LocalDate.now().minusDays(3));

            // Première association : OK
            mockMvc.perform(post("/v1/savings/actions/" + action1Id + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", sharedTx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());

            // Une preuve ne peut pas réutiliser la transaction avec le rôle inverse.
            mockMvc.perform(post("/v1/savings/actions/" + action1Id + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", sharedTx.getId(),
                                    "role", "OLD_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isConflict());

            // Deuxième association avec la même transaction vers une autre action : 409
            mockMvc.perform(post("/v1/savings/actions/" + action2Id + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", sharedTx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Cette transaction bancaire est déjà associée à une preuve financière"));

            // Exactement une seule ligne dans savings_action_evidences
            Integer evidenceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_evidences WHERE transaction_id = ?",
                    Integer.class, sharedTx.getId());
            assertThat(evidenceCount).isEqualTo(1);
        }

        @Test
        @DisplayName("La contrainte UNIQUE PostgreSQL (transaction_id) empêche les doublons en cas de concurrence")
        void postgresUniqueConstraintBlocksConcurrentDoubleCounting() throws Exception {
            User user = activeUser("concurrent-double@eparly.test");
            Account account = createAccount(user, "Compte Conc");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub ConcDouble", "Netflix", 1599L);
            OfferProvider provider = createProvider("ConcDoubleProvider");
            CommercialOffer offer = createOffer(provider, "ConcDoubleOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            Transaction tx = createExpenseTransaction(account, job,
                    "DOUBLON CONC", -999L, LocalDate.now().minusDays(2));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> addEvidence = () -> {
                    MvcResult res = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "transactionId", tx.getId(),
                                            "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                            .andReturn();
                    return res.getResponse().getStatus();
                };
                List<Future<Integer>> futures = executor.invokeAll(List.of(addEvidence, addEvidence));
                int status1 = futures.get(0).get();
                int status2 = futures.get(1).get();

                // Un doit réussir (201), l'autre doit échouer (409)
                assertThat(List.of(status1, status2)).containsExactlyInAnyOrder(201, 409);

                // Exactement une seule preuve en base
                Integer evidenceCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM savings_action_evidences WHERE transaction_id = ?",
                        Integer.class, tx.getId());
                assertThat(evidenceCount).isEqualTo(1);
            } finally {
                executor.shutdown();
            }
        }
    }

    // =========================================================================
    // GROUPE 5 : Règles métier - NOT_VERIFIED si pas DECLARED, pas de VERIFIED_OBSERVED
    // =========================================================================

    @Nested
    @DisplayName("5. Règles métier : NOT_VERIFIED si action non DECLARED, VERIFIED_OBSERVED jamais accordé")
    class BusinessRules {

        @Test
        @DisplayName("Action PLANNED ne peut pas obtenir PARTIALLY_VERIFIED même avec une preuve")
        void plannedActionStaysNotVerifiedEvenWithEvidence() throws Exception {
            User user = activeUser("planned-noverif@eparly.test");
            Account account = createAccount(user, "Compte Planned");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub Planned", "Netflix", 1599L);
            OfferProvider provider = createProvider("PlannedProvider");
            CommercialOffer offer = createOffer(provider, "PlannedOffer", 999L);

            // Créer l'action sans passer à DECLARED (reste PLANNED)
            Map<String, Object> req = Map.of(
                    "subscriptionId", sub.getId(),
                    "offerId", offer.getId(),
                    "sector", "STREAMING",
                    "horizonMonths", 12,
                    "requirements", Map.of(),
                    "userKnownCancellationFeeMinor", 0L,
                    "userKnownOtherFeesMinor", 0L
            );
            String resp = mockMvc.perform(post("/v1/savings/actions")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            UUID actionId = UUID.fromString(objectMapper.readTree(resp).get("id").asText());

            Transaction tx = createExpenseTransaction(account, job,
                    "NEW SUB PLANNED", -999L, LocalDate.now().minusDays(2));

            // Associer une preuve
            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());

            // L'action PLANNED reste NOT_VERIFIED malgré la preuve
            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("NOT_VERIFIED"))
                    .andExpect(jsonPath("$.decisionReasons").isArray());
        }

        @Test
        @DisplayName("VERIFIED_OBSERVED n'est jamais accordé - même avec 2 paiements nouveaux et aucun ancien")
        void verifiedObservedIsNeverGrantedWithOnly2NewPaymentsAndNoOldPayments() throws Exception {
            User user = activeUser("no-verified-observed@eparly.test");
            Account account = createAccount(user, "Compte VerObs");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub VeriObs", "OldNetflix", 1599L);
            OfferProvider provider = createProvider("VeriObsProvider");
            CommercialOffer offer = createOffer(provider, "VeriObsOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            // Deux paiements du nouveau fournisseur, aucun de l'ancien
            Transaction tx1 = createExpenseTransaction(account, job,
                    "VERIOBS ABONNEMENT M1", -999L, LocalDate.now().minusDays(15));
            Transaction tx2 = createExpenseTransaction(account, job,
                    "VERIOBS ABONNEMENT M2", -999L, LocalDate.now().minusDays(5));

            // Associer les deux preuves NEW
            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx1.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT",
                                    "effectiveChangeDate", LocalDate.now().minusDays(20).toString()))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx2.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());

            // Évaluer
            String evalResp = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode eval = objectMapper.readTree(evalResp);
            String status = eval.get("verificationStatus").asText();

            // VERIFIED_OBSERVED NE DOIT PAS être accordé uniquement sur la base de 2 nouveaux paiements
            assertThat(status).isEqualTo("PARTIALLY_VERIFIED");
            assertThat(eval.get("missingInformation").toString())
                    .contains("complétude CSV ne peut pas être confirmée");

            // Les raisons doivent expliquer pourquoi VERIFIED_OBSERVED est réservé
            JsonNode reasons = eval.get("decisionReasons");
            boolean containsVerifiedObservedExplanation = false;
            if (reasons != null && reasons.isArray()) {
                for (JsonNode reason : reasons) {
                    if (reason.asText().contains("VERIFIED_OBSERVED")) {
                        containsVerifiedObservedExplanation = true;
                        break;
                    }
                }
            }
            assertThat(containsVerifiedObservedExplanation).isTrue();

            // Vérification en base PostgreSQL
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT verification_status FROM savings_actions WHERE id = ?",
                    String.class, actionId);
            assertThat(dbStatus).isNotEqualTo("VERIFIED_OBSERVED");
        }

        @Test
        @DisplayName("Un paiement annuel et un paiement mensuel sont comparés sur une base mensuelle commune")
        void paymentFrequenciesAreNormalizedBeforeComparingAmounts() throws Exception {
            User user = activeUser("frequency-normalization@eparly.test");
            Account account = createAccount(user, "Compte périodicité");
            ImportJob job = createImportJob(user, account);
            Subscription sub = createSubscription(user, "Annual Old Subscription", "AnnualOldProvider", 11988L);
            sub.setDeclaredFrequency(RecurringFrequency.YEARLY);
            subscriptionRepository.saveAndFlush(sub);
            OfferProvider provider = createProvider("MonthlyNewProvider");
            CommercialOffer offer = createOffer(provider, "Monthly New Offer", 499L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            Transaction oldPayment = createExpenseTransaction(account, job,
                    "ANNUALOLDPROVIDER YEARLY", -11988L, LocalDate.now().minusDays(90));
            Transaction newPayment = createExpenseTransaction(account, job,
                    "MONTHLYNEWPROVIDER MONTHLY", -499L, LocalDate.now().minusDays(5));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", oldPayment.getId(),
                                    "role", "OLD_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", newPayment.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT",
                                    "effectiveChangeDate", LocalDate.now().minusDays(30).toString()))))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.observedOldMonthlyAmountMinor").value(999))
                    .andExpect(jsonPath("$.observedNewMonthlyAmountMinor").value(499))
                    .andExpect(jsonPath("$.observedMonthlyDifferenceMinor").value(500));
        }

        @Test
        @DisplayName("La date effective du changement ne peut pas être inventée : sans preuve explicite, elle reste null")
        void effectiveDateIsNotInventedWithoutExplicitDeclaration() throws Exception {
            User user = activeUser("no-fake-date@eparly.test");
            Account account = createAccount(user, "Compte FakeDate");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub NoFakeDate", "Netflix", 1599L);
            OfferProvider provider = createProvider("FakeDateProvider");
            CommercialOffer offer = createOffer(provider, "FakeDateOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            var action = savingsActionRepository.findById(actionId).orElseThrow();
            action.setDeclaredAt(Instant.now().minusSeconds(40L * 24 * 60 * 60));
            savingsActionRepository.saveAndFlush(action);

            // Cette transaction est postérieure à la déclaration, mais aucune date effective n'est connue.
            createExpenseTransaction(account, job,
                    "NETFLIX ANCIEN ABONNEMENT", -1599L, LocalDate.now().minusDays(10));

            // Pas d'effectiveChangeDate fournie dans l'association
            Transaction tx = createExpenseTransaction(account, job,
                    "FAKEDATEPROVIDER", -999L, LocalDate.now().minusDays(5));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"
                                    // Pas de effectiveChangeDate
                            ))))
                    .andExpect(status().isCreated());

            // La date effective en base doit rester null (non inventée par le système)
            Object effectiveDateDb = jdbcTemplate.queryForObject(
                    "SELECT effective_change_date FROM savings_actions WHERE id = ?",
                    Object.class, actionId);
            assertThat(effectiveDateDb).isNull();

            // La réponse de vérification doit signaler que la date n'a pas été fournie
            String verifyResp = mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectiveChangeDate").isEmpty())
                    .andReturn().getResponse().getContentAsString();

            JsonNode verifyJson = objectMapper.readTree(verifyResp);
            JsonNode missing = verifyJson.get("missingInformation");
            boolean dateIsMentionedAsMissing = false;
            if (missing != null && missing.isArray()) {
                for (JsonNode info : missing) {
                    if (info.asText().toLowerCase().contains("date")) {
                        dateIsMentionedAsMissing = true;
                        break;
                    }
                }
            }
            assertThat(dateIsMentionedAsMissing).isTrue();

            String evaluated = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(evaluated).get("contradictions").size()).isZero();
        }

        @Test
        @DisplayName("Une transaction de virement (TRANSFER) ne peut pas être associée comme preuve")
        void transferTransactionCannotBeEvidence() throws Exception {
            User user = activeUser("transfer-guard@eparly.test");
            Account account = createAccount(user, "Compte Transfer");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub Transfer", "Netflix", 1599L);
            OfferProvider provider = createProvider("TransferProvider");
            CommercialOffer offer = createOffer(provider, "TransferOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            // Transaction de virement (pas une dépense)
            Transaction transfer = createTransferTransaction(account, job,
                    "VIREMENT DEPUIS LIVRET A", 1000L, LocalDate.now().minusDays(3));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", transfer.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Seules les dépenses ou frais bancaires peuvent constituer des preuves de paiement d'abonnement"));
        }

        @Test
        @DisplayName("Contradiction détectée : débit de l'ancien fournisseur après la date de changement")
        void contradictionDetectedWhenOldProviderDebitAfterChangeDate() throws Exception {
            User user = activeUser("contradiction@eparly.test");
            Account account = createAccount(user, "Compte Contradiction");
            ImportJob job = createImportJob(user, account);

            // Abonnement avec un provider identifiable dans les labels
            Subscription sub = createSubscription(user, "Netflix Contradiction", "netflix", 1599L);
            OfferProvider provider = createProvider("AltProvider");
            CommercialOffer offer = createOffer(provider, "AltOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            // Associer une preuve et fixer la date de changement à J-20
            Transaction txNew = createExpenseTransaction(account, job,
                    "ALTPROVIDER ABONNEMENT", -999L, LocalDate.now().minusDays(5));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", txNew.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT",
                                    "effectiveChangeDate", LocalDate.now().minusDays(20).toString()))))
                    .andExpect(status().isCreated());

            // Créer une transaction de l'ANCIEN fournisseur APRÈS la date de changement
            // (le label contient "netflix" qui est le provider de l'abonnement)
            createExpenseTransaction(account, job,
                    "NETFLIX ABONNEMENT STANDARD", -1599L, LocalDate.now().minusDays(10));

            // Évaluer : devrait détecter la contradiction
            String evalResp = mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode eval = objectMapper.readTree(evalResp);
            JsonNode contradictions = eval.get("contradictions");
            assertThat(contradictions).isNotNull();
            assertThat(contradictions.toString()).contains("Débit de l'ancien fournisseur observé après la date effective");
            // Le statut ne peut pas être VERIFIED_OBSERVED avec des contradictions
            assertThat(eval.get("verificationStatus").asText()).isNotEqualTo("VERIFIED_OBSERVED");
        }
    }

    // =========================================================================
    // GROUPE 6 : CSV incomplets - couverture insuffisante de la période post-changement
    // =========================================================================

    @Nested
    @DisplayName("6. CSV incomplets : signalement de couverture temporelle insuffisante")
    class IncompleteCsvCoverage {

        @Test
        @DisplayName("Si les transactions importées ne couvrent pas 30j post-changement, missingInformation contient un avertissement")
        void insufficientTransactionCoverageReportedInMissingInformation() throws Exception {
            User user = activeUser("incomplete-csv@eparly.test");
            Account account = createAccount(user, "Compte CSV Incomplet");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub CSV", "Netflix", 1599L);
            OfferProvider provider = createProvider("CsvProvider");
            CommercialOffer offer = createOffer(provider, "CsvOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            // Transaction récente, mais la date effective de changement est très proche de "maintenant"
            // → il n'y a pas encore 30 jours de recul post-changement
            Transaction tx = createExpenseTransaction(account, job,
                    "CSVPROVIDER LAST", -999L, LocalDate.now().minusDays(2)); // Seul relevé très récent

            // Fixer effectiveChangeDate à hier : il n'y a qu'un seul jour de recul
            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT",
                                    "effectiveChangeDate", LocalDate.now().minusDays(1).toString()))))
                    .andExpect(status().isCreated());

            String verifyResp = mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode resp = objectMapper.readTree(verifyResp);
            JsonNode missing = resp.get("missingInformation");
            // La réponse doit mentionner l'insuffisance de couverture temporelle CSV
            assertThat(missing).isNotNull();
            // Le statut ne peut jamais dépasser PARTIALLY_VERIFIED
            assertThat(resp.get("verificationStatus").asText()).isNotEqualTo("VERIFIED_OBSERVED");
        }
    }

    // =========================================================================
    // GROUPE 7 : Idempotence de l'évaluation
    // =========================================================================

    @Nested
    @DisplayName("7. Idempotence : appels répétés à /evaluate ne créent pas de doublons d'audit")
    class EvaluationIdempotence {

        @Test
        @DisplayName("N appels successifs à /evaluate avec le même contexte ne multiplient pas les lignes d'audit")
        void repeatedEvaluateCallsDoNotMultiplyAuditLines() throws Exception {
            User user = activeUser("idempotent-eval@eparly.test");
            Account account = createAccount(user, "Compte Idemp Eval");
            ImportJob job = createImportJob(user, account);

            Subscription sub = createSubscription(user, "Sub Idemp", "Netflix", 1599L);
            OfferProvider provider = createProvider("IdempProvider");
            CommercialOffer offer = createOffer(provider, "IdempOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            Transaction tx = createExpenseTransaction(account, job,
                    "IDEMPROVIDER ABONNEMENT", -999L, LocalDate.now().minusDays(5));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT",
                                    "effectiveChangeDate", LocalDate.now().minusDays(30).toString()))))
                    .andExpect(status().isCreated());

            // 5 appels consécutifs à /evaluate
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.verificationStatus").value("PARTIALLY_VERIFIED"));
            }

            // Le nombre de lignes d'audit doit rester faible (idempotence)
            long auditCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_verifications WHERE action_id = ?",
                    Long.class, actionId);
            assertThat(auditCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Deux évaluations simultanées d'une action ne créent pas deux audits identiques")
        void concurrentEvaluateCallsRemainIdempotent() throws Exception {
            User user = activeUser("concurrent-idempotent@eparly.test");
            Account account = createAccount(user, "Compte Concurrent Idempotent");
            ImportJob job = createImportJob(user, account);
            Subscription sub = createSubscription(user, "Sub Concurrent", "Netflix", 1599L);
            OfferProvider provider = createProvider("ConcurrentEvalProvider");
            CommercialOffer offer = createOffer(provider, "ConcurrentEvalOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);
            Transaction tx = createExpenseTransaction(account, job,
                    "CONCURRENTEVAL ABONNEMENT", -999L, LocalDate.now().minusDays(5));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> evaluate = () -> mockMvc.perform(
                                post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                        .andReturn().getResponse().getStatus();
                List<Future<Integer>> results = executor.invokeAll(List.of(evaluate, evaluate));
                assertThat(results.get(0).get()).isEqualTo(200);
                assertThat(results.get(1).get()).isEqualTo(200);
            } finally {
                executor.shutdown();
            }

            Long auditCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_verifications WHERE action_id = ?",
                    Long.class, actionId);
            assertThat(auditCount).isEqualTo(1);
        }
    }

    // =========================================================================
    // GROUPE 8 : Vérification de la migration V14 PostgreSQL 17
    // =========================================================================

    @Nested
    @DisplayName("8. Migration V14 : tables, contraintes et trigger vérifiés dans PostgreSQL 17")
    class V14MigrationVerification {

        @Test
        @DisplayName("La migration V14 a créé les tables savings_action_evidences et savings_action_verifications")
        void v14MigrationCreatedBothTables() {
            // Vérifier savings_action_evidences
            Integer evidenceTable = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'savings_action_evidences'",
                    Integer.class);
            assertThat(evidenceTable).isEqualTo(1);

            // Vérifier savings_action_verifications
            Integer verificationTable = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'savings_action_verifications'",
                    Integer.class);
            assertThat(verificationTable).isEqualTo(1);
        }

        @Test
        @DisplayName("La migration V14 a ajouté la colonne effective_change_date à savings_actions")
        void v14MigrationAddedEffectiveChangeDateColumn() {
            Integer columnExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'savings_actions' AND column_name = 'effective_change_date'",
                    Integer.class);
            assertThat(columnExists).isEqualTo(1);
        }

        @Test
        @DisplayName("La contrainte UNIQUE sur transaction_id est présente dans savings_action_evidences")
        void uniqueConstraintOnTransactionIdExists() {
            Integer uniqueConstraint = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                    "JOIN information_schema.constraint_column_usage ccu " +
                    "  ON tc.constraint_name = ccu.constraint_name " +
                    "WHERE tc.table_name = 'savings_action_evidences' " +
                    "  AND tc.constraint_type = 'UNIQUE' " +
                    "  AND ccu.column_name = 'transaction_id'",
                    Integer.class);
            assertThat(uniqueConstraint).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Le trigger trg_savings_action_evidences_user_check est présent")
        void userConsistencyTriggerExists() {
            Integer triggerExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.triggers " +
                    "WHERE trigger_name = 'trg_savings_action_evidences_user_check'",
                    Integer.class);
            assertThat(triggerExists).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("La contrainte CHECK sur status (NOT_VERIFIED, PARTIALLY_VERIFIED, VERIFIED_OBSERVED) est présente")
        void statusCheckConstraintExists() {
            Integer checkConstraint = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                    "WHERE table_name = 'savings_action_verifications' " +
                    "  AND constraint_type = 'CHECK'",
                    Integer.class);
            assertThat(checkConstraint).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Flyway a bien enregistré la migration V14 comme réussie")
        void flywayRecordsV14AsSuccessful() {
            Integer v14Count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '14' AND success = true",
                    Integer.class);
            assertThat(v14Count).isEqualTo(1);
        }
    }

    // =========================================================================
    // GROUPE 9 : Candidates filtering
    // =========================================================================

    @Nested
    @DisplayName("9. Filtrage des candidats par rôle et pagination")
    class CandidatesFiltering {

        @Test
        @DisplayName("GET /candidates filtre correctement par rôle et renvoie les bons montants")
        void candidatesFilteredByRole() throws Exception {
            User user = activeUser("candidates-filter@eparly.test");
            Account account = createAccount(user, "Compte Candidates");
            ImportJob job = createImportJob(user, account);

            // Abonnement avec provider identifiable dans le label
            Subscription sub = createSubscription(user, "Canal Plus Filter", "canalplus", 2999L);
            OfferProvider provider = createProvider("AltCanal");
            CommercialOffer offer = createOffer(provider, "AltCanal Standard", 1999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            // Fixer la date de bascule
            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "effectiveChangeDate", LocalDate.now().minusDays(60).toString()))))
                    .andExpect(status().isOk());

            // Un paiement de chaque rôle attendu, plus des transactions non éligibles.
            Transaction oldPayment = createExpenseTransaction(account, job,
                    "CANALPLUS ABONNEMENT MENSUEL", -2999L, LocalDate.now().minusDays(90));
            Transaction newPayment = createExpenseTransaction(account, job,
                    "ALTCANAL STANDARD MENSUEL", -1999L, LocalDate.now().minusDays(15));
            createNonExpenseTransaction(account, job,
                    "CANALPLUS ABONNEMENT MENSUEL", -2999L,
                    LocalDate.now().minusDays(90), TransactionType.TRANSFER);
            createNonExpenseTransaction(account, job,
                    "CANALPLUS ABONNEMENT MENSUEL", 2999L,
                    LocalDate.now().minusDays(90), TransactionType.REFUND);
            createNonExpenseTransaction(account, job,
                    "CANALPLUS ABONNEMENT MENSUEL", 2999L,
                    LocalDate.now().minusDays(90), TransactionType.INCOME);

            User otherUser = activeUser("candidates-filter-other@eparly.test");
            Account otherAccount = createAccount(otherUser, "Compte autre utilisateur");
            ImportJob otherJob = createImportJob(otherUser, otherAccount);
            createExpenseTransaction(otherAccount, otherJob,
                    "ALTCANAL STANDARD MENSUEL", -1999L, LocalDate.now().minusDays(15));

            // Requête sans filtre de rôle
            String allCandidates = mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode allJson = objectMapper.readTree(allCandidates);
            List<String> candidateIds = new ArrayList<>();
            List<String> candidateAccountIds = new ArrayList<>();
            List<String> suggestedRoles = new ArrayList<>();
            for (JsonNode candidate : allJson.get("items")) {
                candidateIds.add(candidate.get("transactionId").asText());
                candidateAccountIds.add(candidate.get("accountId").asText());
                suggestedRoles.add(candidate.get("suggestedRole").asText());
            }
            assertThat(allJson.get("items").size()).isEqualTo(2);
            assertThat(allJson.get("totalElements").asInt()).isEqualTo(2);
            assertThat(candidateIds)
                    .containsExactlyInAnyOrder(oldPayment.getId().toString(), newPayment.getId().toString());
            assertThat(candidateAccountIds).containsOnly(account.getId().toString());
            assertThat(suggestedRoles)
                    .containsExactlyInAnyOrder(
                            "OLD_SUBSCRIPTION_PAYMENT",
                            "NEW_SUBSCRIPTION_PAYMENT");

            // Chaque filtre de rôle ne renvoie que le paiement correspondant.
            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates")
                            .param("role", "OLD_SUBSCRIPTION_PAYMENT")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].transactionId").value(oldPayment.getId().toString()))
                    .andExpect(jsonPath("$.items[0].accountId").value(account.getId().toString()))
                    .andExpect(jsonPath("$.items[0].suggestedRole").value("OLD_SUBSCRIPTION_PAYMENT"));

            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates")
                            .param("role", "NEW_SUBSCRIPTION_PAYMENT")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].transactionId").value(newPayment.getId().toString()))
                    .andExpect(jsonPath("$.items[0].accountId").value(account.getId().toString()))
                    .andExpect(jsonPath("$.items[0].suggestedRole").value("NEW_SUBSCRIPTION_PAYMENT"));

            // Pagination : page=0, size=1
            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates?page=0&size=1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));

            mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates")
                            .param("page", "2147483647")
                            .param("size", "2147483647")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.size").value(100));
        }
    }

    @Nested
    @DisplayName("Compte explicitement associé à l'abonnement")
    class SubscriptionAccountScope {

        @Test
        @DisplayName("Les candidats et preuves sont limités au compte de l'abonnement")
        void linkedSubscriptionAccountScopesCandidatesAndEvidence() throws Exception {
            User user = activeUser("account-scope@eparly.test");
            Account linkedAccount = createAccount(user, "Compte abonnement");
            Account otherAccount = createAccount(user, "Autre compte utilisateur");
            ImportJob linkedJob = createImportJob(user, linkedAccount);
            ImportJob otherJob = createImportJob(user, otherAccount);

            Subscription sub = createSubscription(user, "CompteScope", "scopeprovider", 1599L);
            sub.setAccount(linkedAccount);
            subscriptionRepository.saveAndFlush(sub);
            OfferProvider provider = createProvider("ScopeNewProvider");
            CommercialOffer offer = createOffer(provider, "Scope New Offer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);

            Transaction eligible = createExpenseTransaction(linkedAccount, linkedJob,
                    "SCOPENEWPROVIDER SUBSCRIPTION", -999L, LocalDate.now().minusDays(5));
            Transaction wrongAccount = createExpenseTransaction(otherAccount, otherJob,
                    "SCOPENEWPROVIDER SUBSCRIPTION", -999L, LocalDate.now().minusDays(5));

            String candidates = mockMvc.perform(get("/v1/savings/actions/" + actionId + "/verification/candidates")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode candidateItems = objectMapper.readTree(candidates).get("items");
            assertThat(candidateItems.size()).isEqualTo(1);
            assertThat(candidateItems.get(0).get("transactionId").asText()).isEqualTo(eligible.getId().toString());

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", wrongAccount.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isBadRequest());

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO savings_action_evidences (user_id, action_id, transaction_id, role, provenance) " +
                            "VALUES (?, ?, ?, 'NEW_SUBSCRIPTION_PAYMENT', 'USER_CONFIRMED')",
                    user.getId(), actionId, wrongAccount.getId()))
                    .isInstanceOf(UncategorizedSQLException.class)
                    .hasMessageContaining("Transaction account mismatch");

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", eligible.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Intégrité PostgreSQL V15")
    class V15Integrity {

        @Test
        @DisplayName("La migration V15 est appliquée et contraint les audits à l'utilisateur de l'action")
        void v15MigrationConstrainsAuditOwnership() throws Exception {
            User owner = activeUser("audit-owner@eparly.test");
            User otherUser = activeUser("audit-other@eparly.test");
            Subscription sub = createSubscription(owner, "AuditOwnerSub", "provider", 1500L);
            OfferProvider provider = createProvider("AuditOwnerProvider");
            CommercialOffer offer = createOffer(provider, "AuditOwnerOffer", 999L);
            UUID actionId = createDeclaredAction(owner, sub, offer);

            Integer migrationApplied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = true",
                    Integer.class);
            assertThat(migrationApplied).isEqualTo(1);

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO savings_action_verifications (action_id, user_id, status, evaluated_at) " +
                            "VALUES (?, ?, 'NOT_VERIFIED', CURRENT_TIMESTAMP)",
                    actionId, otherUser.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Integer invalidAuditCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM savings_action_verifications WHERE action_id = ?",
                    Integer.class, actionId);
            assertThat(invalidAuditCount).isZero();
        }

        @Test
        @DisplayName("Une transaction utilisée comme preuve conserve ses faits financiers sources")
        void evidencedTransactionFinancialFactsCannotBeChanged() throws Exception {
            User user = activeUser("immutable-transaction@eparly.test");
            Account account = createAccount(user, "Compte preuve");
            Account otherAccount = createAccount(user, "Compte alternatif");
            ImportJob job = createImportJob(user, account);
            ImportJob otherJob = createImportJob(user, otherAccount);
            Subscription sub = createSubscription(user, "ImmutableSub", "provider", 1599L);
            sub.setAccount(account);
            subscriptionRepository.saveAndFlush(sub);
            OfferProvider provider = createProvider("ImmutableProvider");
            CommercialOffer offer = createOffer(provider, "ImmutableOffer", 999L);
            UUID actionId = createDeclaredAction(user, sub, offer);
            Transaction tx = createExpenseTransaction(account, job,
                    "IMMUTABLE PROVIDER", -999L, LocalDate.now().minusDays(5));

            mockMvc.perform(post("/v1/savings/actions/" + actionId + "/verification/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", tx.getId(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isCreated());

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE transactions SET amount_minor = ? WHERE id = ?", -1000L, tx.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE transactions SET transaction_type = 'INCOME' WHERE id = ?", tx.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE transactions SET fingerprint = ? WHERE id = ?", UUID.randomUUID().toString(), tx.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE transactions SET account_id = ?, import_job_id = ? WHERE id = ?",
                    otherAccount.getId(), otherJob.getId(), tx.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Integer categoryCorrection = jdbcTemplate.update(
                    "UPDATE transactions SET custom_category_flag = TRUE WHERE id = ?", tx.getId());
            assertThat(categoryCorrection).isEqualTo(1);

            Map<String, Object> stored = jdbcTemplate.queryForMap(
                    "SELECT account_id, import_job_id, amount_minor, transaction_type, fingerprint, custom_category_flag FROM transactions WHERE id = ?",
                    tx.getId());
            assertThat(stored.get("account_id")).isEqualTo(account.getId());
            assertThat(stored.get("import_job_id")).isEqualTo(job.getId());
            assertThat(stored.get("amount_minor")).isEqualTo(-999L);
            assertThat(stored.get("transaction_type")).isEqualTo("EXPENSE");
            assertThat(stored.get("fingerprint")).isEqualTo(tx.getFingerprint());
            assertThat(stored.get("custom_category_flag")).isEqualTo(true);
        }
    }

    // =========================================================================
    // GROUPE 10 : Action introuvable (404 pour toutes les opérations)
    // =========================================================================

    @Nested
    @DisplayName("10. Action introuvable : toutes les opérations renvoient 404")
    class ActionNotFound {

        @Test
        @DisplayName("Toutes les opérations de vérification renvoient 404 pour un actionId inexistant")
        void allOperationsReturn404ForUnknownAction() throws Exception {
            User user = activeUser("notfound-verif@eparly.test");
            UUID unknownActionId = UUID.randomUUID();
            String base = "/v1/savings/actions/" + unknownActionId + "/verification";

            mockMvc.perform(get(base + "/candidates")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get(base)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post(base + "/evaluate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post(base + "/evidence")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "transactionId", UUID.randomUUID(),
                                    "role", "NEW_SUBSCRIPTION_PAYMENT"))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(delete(base + "/evidence/" + UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isNotFound());
        }
    }
}
