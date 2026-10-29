package com.eparly.api.savings.action;

import com.eparly.api.TestJwtConfiguration;
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
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.domain.SavingsActionHistory;
import com.eparly.api.savings.action.domain.VerificationStatus;
import com.eparly.api.savings.action.dto.SavingsEstimateSnapshot;
import com.eparly.api.savings.action.repository.SavingsActionHistoryRepository;
import com.eparly.api.savings.action.repository.SavingsActionRepository;
import com.eparly.api.savings.domain.CostSource;
import com.eparly.api.savings.domain.SavingsStatus;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class SavingsActionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private OfferProviderRepository providerRepository;
    @Autowired private CommercialOfferRepository offerRepository;
    @Autowired private OfferPricingRepository pricingRepository;
    @Autowired private OfferEvidenceRepository evidenceRepository;
    @Autowired private SavingsActionRepository savingsActionRepository;
    @Autowired private SavingsActionHistoryRepository historyRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /* ---------- Helpers de test ---------- */

    private User activeUser(String email) {
        User user = new User(email, "PasswordHash2026!Secure");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("eparly-test")
                .subject(user.getId().toString())
                .audience(List.of("eparly-test-client"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims
        )).getTokenValue();
        return "Bearer " + token;
    }

    private Subscription createSubscription(User user, String name, String provider, Long declaredAmount) {
        Subscription s = new Subscription();
        s.setUser(user);
        s.setName(name);
        s.setProvider(provider);
        s.setDeclaredAmountMinor(declaredAmount);
        s.setCurrency("EUR");
        s.setDeclaredFrequency(RecurringFrequency.MONTHLY);
        s.setDeclaredStatus(SubscriptionStatus.ACTIVE);
        s.setOrigin(SubscriptionOrigin.USER_DECLARED);
        return subscriptionRepository.saveAndFlush(s);
    }

    private OfferProvider createProvider(String name, OfferSector sector, String website) {
        OfferProvider p = new OfferProvider();
        p.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        p.setSector(sector);
        p.setOfficialWebsite(website);
        p.setCountry("FR");
        p.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.saveAndFlush(p);
    }

    private CommercialOffer createOffer(OfferProvider provider, String name, OfferSector sector,
                                        LocalDate validFrom, LocalDate validUntil) {
        CommercialOffer o = new CommercialOffer();
        o.setProvider(provider);
        o.setCommercialName(name);
        o.setSector(sector);
        o.setCharacteristics("{\"screens\": 2, \"resolution\": \"HD\"}");
        o.setEligibilityConditions("Aucune");
        o.setCommitment("SANS_ENGAGEMENT");
        o.setTerritory("FR");
        o.setPublicationStatus(OfferStatus.VERIFIED);
        o.setValidFrom(validFrom);
        o.setValidUntil(validUntil);
        return offerRepository.saveAndFlush(o);
    }

    private OfferPricing createPricing(CommercialOffer offer, Long amountMinor, Long activationFee) {
        OfferPricing p = new OfferPricing();
        p.setOffer(offer);
        p.setAmountMinor(amountMinor);
        p.setCurrency("EUR");
        p.setBillingPeriod(OfferBillingPeriod.MONTHLY);
        p.setActivationFeeMinor(activationFee != null ? activationFee : 0L);
        p.setTaxesIncluded(true);
        p.setDataUnknown(false);
        return pricingRepository.saveAndFlush(p);
    }

    private OfferEvidence createEvidence(CommercialOffer offer, String url) {
        OfferEvidence e = new OfferEvidence();
        e.setOffer(offer);
        e.setOfficialUrl(url);
        e.setConsultedAt(Instant.now());
        e.setVerifiedAt(Instant.now());
        e.setNature("Grille tarifaire officielle");
        e.setVerifiedSummary("Vérifié conforme");
        e.setVerificationStatus(EvidenceStatus.VERIFIED);
        e.setProvenance("Site officiel opérateur");
        return evidenceRepository.saveAndFlush(e);
    }

    /* ---------- PHASE 1 — Test d'intégration critique ---------- */

    @Test
    @DisplayName("Scénario complet de création d'action, écriture JSONB, flush/clear et validation d'intégrité")
    void testCreateActionWithJsonbSnapshotAndPersistenceVerification() throws Exception {
        // 1. Préparer un utilisateur et un abonnement lui appartenant
        User user = activeUser("phase1-test@eparly.test");
        Subscription subscription = createSubscription(user, "Abonnement Streaming Standard", "StreamCurrent", 1599L);

        // 2. Préparer une offre fictive vérifiée et compatible
        OfferProvider provider = createProvider("StreamAltProvider", OfferSector.STREAMING, "https://stream-alt.com");
        CommercialOffer offer = createOffer(provider, "StreamAlt Eco", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L); // 9.99€/mois, 0€ activation
        createEvidence(offer, "https://stream-alt.com/tarifs");

        // 3. Préparer des frais explicitement connus
        Long userKnownCancellationFeeMinor = 0L;
        Long userKnownOtherFeesMinor = 0L;

        // 4. Créer une action via l'API avec un JWT de test
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("subscriptionId", subscription.getId());
        requestBody.put("offerId", offer.getId());
        requestBody.put("sector", "STREAMING");
        requestBody.put("horizonMonths", 12);
        requestBody.put("requirements", Map.of());
        requestBody.put("userKnownCancellationFeeMinor", userKnownCancellationFeeMinor);
        requestBody.put("userKnownOtherFeesMinor", userKnownOtherFeesMinor);
        requestBody.put("notes", "Action planifiée lors du test Phase 1");

        // 5. Vérifier HTTP 201
        String responseContent = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.verificationStatus").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.horizonMonths").value(12))
                .andExpect(jsonPath("$.estimateSnapshot").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode responseJson = objectMapper.readTree(responseContent);
        UUID actionId = UUID.fromString(responseJson.get("id").asText());

        // 6. Vérifier qu'une ligne existe dans savings_actions
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, user_id, subscription_id, alternative_offer_id, status, verification_status, " +
                        "horizon_months, notes, estimate_snapshot FROM savings_actions WHERE id = ?",
                actionId
        );
        assertThat(row).isNotNull();
        assertThat(row.get("id")).isEqualTo(actionId);
        assertThat(row.get("user_id")).isEqualTo(user.getId());
        assertThat(row.get("subscription_id")).isEqualTo(subscription.getId());
        assertThat(row.get("alternative_offer_id")).isEqualTo(offer.getId());
        assertThat(row.get("status")).isEqualTo("PLANNED");
        assertThat(row.get("verification_status")).isEqualTo("NOT_VERIFIED");
        assertThat(((Number) row.get("horizon_months")).intValue()).isEqualTo(12);
        assertThat(row.get("notes")).isEqualTo("Action planifiée lors du test Phase 1");

        // 7. Vérifier que le snapshot JSONB est réellement écrit
        Object rawJsonb = row.get("estimate_snapshot");
        assertThat(rawJsonb).isNotNull();
        String jsonbText = jdbcTemplate.queryForObject(
                "SELECT estimate_snapshot::text FROM savings_actions WHERE id = ?",
                String.class,
                actionId
        );
        assertThat(jsonbText).isNotBlank();
        JsonNode pgJson = objectMapper.readTree(jsonbText);
        assertThat(pgJson.get("potentialNetSavingsMinor").asLong()).isEqualTo(7200L);
        assertThat(pgJson.get("currency").asText()).isEqualTo("EUR");
        assertThat(pgJson.get("officialUrl").asText()).isEqualTo("https://stream-alt.com/tarifs");

        // 8. Exécuter flush() puis clear() sur l'EntityManager
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });

        // 9. Recharger l'action depuis PostgreSQL
        SavingsAction reloaded = savingsActionRepository.findById(actionId).orElseThrow();

        // 10. Vérifier l'intégrité du snapshot : devise, horizon, montants, statut d'estimation, dates et provenance
        SavingsEstimateSnapshot snapshot = reloaded.getEstimateSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.currency()).isEqualTo("EUR");
        assertThat(snapshot.horizonMonths()).isEqualTo(12);
        // Coût actuel : 15.99€ * 12 = 191.88€ (19188 centimes)
        assertThat(snapshot.currentTotalCostMinor()).isEqualTo(19188L);
        assertThat(snapshot.currentCostSource()).isEqualTo(CostSource.USER_DECLARED);
        // Coût alternatif : 9.99€ * 12 = 119.88€ (11988 centimes)
        assertThat(snapshot.alternativeTotalCostMinor()).isEqualTo(11988L);
        // Frais de changement : 0 centimes
        assertThat(snapshot.switchingCostMinor()).isEqualTo(0L);
        // Économie nette : 19188 - 11988 - 0 = 7200 centimes (72.00€)
        assertThat(snapshot.potentialNetSavingsMinor()).isEqualTo(7200L);
        assertThat(snapshot.estimationStatus()).isEqualTo(SavingsStatus.SAVINGS_POSSIBLE);
        // Dates et provenance
        assertThat(snapshot.offerVerifiedAt()).isNotNull();
        assertThat(snapshot.estimatedAt()).isNotNull();
        assertThat(snapshot.officialUrl()).isEqualTo("https://stream-alt.com/tarifs");

        // 11. Vérifier l'événement d'historique initial
        List<SavingsActionHistory> history = historyRepository.findAllByActionIdOrderByCreatedAtAsc(actionId);
        assertThat(history).hasSize(1);
        SavingsActionHistory initialEvent = history.get(0);
        assertThat(initialEvent.getPreviousStatus()).isNull();
        assertThat(initialEvent.getNewStatus()).isEqualTo(ActionStatus.PLANNED);
        assertThat(initialEvent.getEventType()).isEqualTo("CREATED");
        assertThat(initialEvent.getCreatedAt()).isNotNull();
        assertThat(initialEvent.getDetails()).isEqualTo("Action créée");

        // 12. Vérifier que le montant est recalculé par le backend
        assertThat(snapshot.potentialNetSavingsMinor())
                .isEqualTo(snapshot.currentTotalCostMinor() - snapshot.alternativeTotalCostMinor() - snapshot.switchingCostMinor());
    }

    /* ---------- PHASE 2 — Parcours fonctionnels ---------- */

    @Test
    @DisplayName("Création refusée si économie nulle ou négative")
    void testCreationRefusedWhenNoOrNegativeSavings() throws Exception {
        User user = activeUser("nosavings@eparly.test");
        Subscription subscription = createSubscription(user, "Abonnement Bas", "StreamLow", 799L); // 7.99€

        OfferProvider provider = createProvider("StreamHighProvider", OfferSector.STREAMING, "https://stream-high.com");
        CommercialOffer offer = createOffer(provider, "StreamHigh Luxe", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 1599L, 0L); // 15.99€ -> surcoût, pas d'économie
        createEvidence(offer, "https://stream-high.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aucun gain net positif estimé pour cette offre"));
    }

    @Test
    @DisplayName("Création refusée si l'offre est incompatible")
    void testCreationRefusedWhenOfferIncompatible() throws Exception {
        User user = activeUser("incomp@eparly.test");
        Subscription subscription = createSubscription(user, "Streaming Duo", "ProviderA", 1999L);

        OfferProvider provider = createProvider("StreamIncompProvider", OfferSector.STREAMING, "https://stream-inc.com");
        CommercialOffer offer = createOffer(provider, "Stream 1 Écran", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        // characteristics: 2 screens
        createPricing(offer, 599L, 0L);
        createEvidence(offer, "https://stream-inc.com/tarifs");

        // Requirement demands 4 screens -> incompatible with 2 screens
        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of("minScreens", 4),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'offre alternative n'est pas compatible avec les critères de l'utilisateur"));
    }

    @Test
    @DisplayName("Création refusée si des frais obligatoires sont inconnus")
    void testCreationRefusedWhenMandatoryFeesUnknown() throws Exception {
        User user = activeUser("unknownfees@eparly.test");
        Subscription subscription = createSubscription(user, "Streaming Frais", "ProviderF", 1999L);

        OfferProvider provider = createProvider("StreamAltFees", OfferSector.STREAMING, "https://stream-fees.com");
        CommercialOffer offer = createOffer(provider, "Stream Alt 2", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-fees.com/tarifs");

        // Missing userKnownCancellationFeeMinor and userKnownOtherFeesMinor
        Map<String, Object> req = new HashMap<>();
        req.put("subscriptionId", subscription.getId());
        req.put("offerId", offer.getId());
        req.put("sector", "STREAMING");
        req.put("horizonMonths", 12);
        req.put("requirements", Map.of());
        // omit userKnownCancellationFeeMinor and otherFees -> null

        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Données insuffisantes pour créer une action d'économie"));
    }

    @Test
    @DisplayName("Création refusée pour une offre périmée")
    void testCreationRefusedForExpiredOffer() throws Exception {
        User user = activeUser("expired@eparly.test");
        Subscription subscription = createSubscription(user, "Streaming Expiré", "ProviderExp", 1999L);

        OfferProvider provider = createProvider("StreamExpProvider", OfferSector.STREAMING, "https://stream-exp.com");
        // Offer ended yesterday
        CommercialOffer offer = createOffer(provider, "Stream Ancienne Promo", OfferSector.STREAMING,
                LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-exp.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Conservation du snapshot après changement du prix catalogue")
    void testSnapshotPreservedAfterCatalogPriceChange() throws Exception {
        User user = activeUser("catalogchange@eparly.test");
        Subscription subscription = createSubscription(user, "Streaming Stable", "StreamCur", 1999L);

        OfferProvider provider = createProvider("StreamCatalogProvider", OfferSector.STREAMING, "https://stream-cat.com");
        CommercialOffer offer = createOffer(provider, "Stream Promo", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        OfferPricing pricing = createPricing(offer, 999L, 0L); // 9.99€ initially
        createEvidence(offer, "https://stream-cat.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        String createResp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID actionId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        // Now modify catalogue price to 29.99€ (2999 centimes)
        pricing.setAmountMinor(2999L);
        pricingRepository.saveAndFlush(pricing);

        // Fetch action via GET endpoint
        mockMvc.perform(get("/v1/savings/actions/" + actionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimateSnapshot.alternativeTotalCostMinor").value(11988))
                .andExpect(jsonPath("$.estimateSnapshot.potentialNetSavingsMinor").value(12000));
        // Note: 1999*12 = 23988, 999*12 = 11988, net = 12000. Remains completely unchanged!
    }

    @Test
    @DisplayName("Transitions de cycle de vie : PLANNED → COMMITTED → DECLARED → CANCELED et traçabilité historique")
    void testLifecyclePlannedToCommittedToDeclaredAndCancel() throws Exception {
        User user = activeUser("lifecycle@eparly.test");
        Subscription subscription = createSubscription(user, "Stream Life", "ProviderLife", 1599L);

        OfferProvider provider = createProvider("StreamLifeProvider", OfferSector.STREAMING, "https://stream-life.com");
        CommercialOffer offer = createOffer(provider, "Stream Life Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-life.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        String createResp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString();

        UUID actionId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        // 1. PLANNED → COMMITTED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED", "notes", "Souscription entamée"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"))
                .andExpect(jsonPath("$.committedAt").isNotEmpty());

        // 2. COMMITTED → DECLARED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DECLARED", "notes", "Nouvelle offre active"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLARED"))
                .andExpect(jsonPath("$.declaredAt").isNotEmpty());

        // 3. DECLARED → CANCELED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELED", "notes", "Rétractation finale"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.canceledAt").isNotEmpty());

        // Vérification de la persistance de l'historique complet
        List<SavingsActionHistory> history = historyRepository.findAllByActionIdOrderByCreatedAtAsc(actionId);
        assertThat(history).hasSize(4);
        assertThat(history.get(0).getEventType()).isEqualTo("CREATED");
        assertThat(history.get(0).getNewStatus()).isEqualTo(ActionStatus.PLANNED);

        assertThat(history.get(1).getEventType()).isEqualTo("STATUS_CHANGE");
        assertThat(history.get(1).getPreviousStatus()).isEqualTo(ActionStatus.PLANNED);
        assertThat(history.get(1).getNewStatus()).isEqualTo(ActionStatus.COMMITTED);

        assertThat(history.get(2).getEventType()).isEqualTo("STATUS_CHANGE");
        assertThat(history.get(2).getPreviousStatus()).isEqualTo(ActionStatus.COMMITTED);
        assertThat(history.get(2).getNewStatus()).isEqualTo(ActionStatus.DECLARED);

        assertThat(history.get(3).getEventType()).isEqualTo("STATUS_CHANGE");
        assertThat(history.get(3).getPreviousStatus()).isEqualTo(ActionStatus.DECLARED);
        assertThat(history.get(3).getNewStatus()).isEqualTo(ActionStatus.CANCELED);
    }

    @Test
    @DisplayName("Transitions de statut interdites rejetées avec 400 Bad Request")
    void testForbiddenTransitionsRejected() throws Exception {
        User user = activeUser("forbidden@eparly.test");
        Subscription subscription = createSubscription(user, "Stream Forb", "ProviderForb", 1599L);

        OfferProvider provider = createProvider("StreamForbProvider", OfferSector.STREAMING, "https://stream-forb.com");
        CommercialOffer offer = createOffer(provider, "Stream Forb Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-forb.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        String createResp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID actionId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        // Saut direct interdit : PLANNED → DECLARED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DECLARED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transition non autorisée de PLANNED vers DECLARED"));

        // Annulation directe autorisée : PLANNED → CANCELED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk());

        // Transition interdite depuis CANCELED vers n'importe quel autre statut
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PLANNED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Une action annulée ne peut pas passer à un autre statut"));
    }

    @Test
    @DisplayName("Idempotence de mise à jour : renvoyer le même statut ne recrée pas d'historique superflu")
    void testStatusUpdateIdempotence() throws Exception {
        User user = activeUser("idempotence@eparly.test");
        Subscription subscription = createSubscription(user, "Stream Idemp", "ProviderIdemp", 1599L);

        OfferProvider provider = createProvider("StreamIdempProvider", OfferSector.STREAMING, "https://stream-idemp.com");
        CommercialOffer offer = createOffer(provider, "Stream Idemp Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-idemp.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        String createResp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID actionId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        // Passage à COMMITTED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED"))))
                .andExpect(status().isOk());

        int historyCountBefore = historyRepository.findAllByActionIdOrderByCreatedAtAsc(actionId).size();

        // Répétition idempotente de COMMITTED
        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED", "notes", "Note mise à jour sans transition"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"))
                .andExpect(jsonPath("$.notes").value("Note mise à jour sans transition"));

        int historyCountAfter = historyRepository.findAllByActionIdOrderByCreatedAtAsc(actionId).size();
        assertThat(historyCountAfter).isEqualTo(historyCountBefore); // Aucun événement superflu
    }

    @Test
    @DisplayName("Prévention des actions actives en doublon pour la même opportunité et déblocage après annulation")
    void testDuplicateActiveActionPreventedAndAllowedAfterCancel() throws Exception {
        User user = activeUser("duplicate@eparly.test");
        Subscription subscription = createSubscription(user, "Stream Dup", "ProviderDup", 1599L);

        OfferProvider provider = createProvider("StreamDupProvider", OfferSector.STREAMING, "https://stream-dup.com");
        CommercialOffer offer = createOffer(provider, "Stream Dup Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-dup.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        // Première création : OK
        String firstResp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID firstActionId = UUID.fromString(objectMapper.readTree(firstResp).get("id").asText());

        // Deuxième création avec action active existante : 409 CONFLICT
        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Une action existe déjà pour cet abonnement et cette offre alternative"));

        // Annulation de la première action
        mockMvc.perform(patch("/v1/savings/actions/" + firstActionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk());

        // Nouvelle création désormais autorisée car l'ancienne n'est plus active
        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Création concurrente d'une même opportunité : contrainte PostgreSQL et comportement HTTP 409")
    void testConcurrentCreationUniqueConstraintConflict() throws Exception {
        User user = activeUser("concurrency@eparly.test");
        Subscription subscription = createSubscription(user, "Stream Concurr", "ProviderConc", 1599L);

        OfferProvider provider = createProvider("StreamConcProvider", OfferSector.STREAMING, "https://stream-conc.com");
        CommercialOffer offer = createOffer(provider, "Stream Conc Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-conc.com/tarifs");

        Map<String, Object> req = Map.of(
                "subscriptionId", subscription.getId(),
                "offerId", offer.getId(),
                "sector", "STREAMING",
                "horizonMonths", 12,
                "requirements", Map.of(),
                "userKnownCancellationFeeMinor", 0L,
                "userKnownOtherFeesMinor", 0L
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> {
                MvcResult res = mockMvc.perform(post("/v1/savings/actions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                return res.getResponse().getStatus();
            };

            List<Future<Integer>> futures = executor.invokeAll(List.of(task, task));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }

            // Un doit réussir avec 201, l'autre doit échouer avec 409
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);

            // Exactement une action active enregistrée en base
            List<SavingsAction> activeInDb = savingsActionRepository.findActiveActionsBySubscriptionAndOffer(
                    subscription.getId(), offer.getId());
            assertThat(activeInDb).hasSize(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Pagination et filtres par statut et abonnement sur GET /v1/savings/actions")
    void testListPaginationAndFilters() throws Exception {
        User user = activeUser("pagination@eparly.test");
        Subscription sub1 = createSubscription(user, "Sub One", "Provider1", 1599L);
        Subscription sub2 = createSubscription(user, "Sub Two", "Provider2", 1999L);

        OfferProvider provider = createProvider("StreamPagProvider", OfferSector.STREAMING, "https://stream-pag.com");
        CommercialOffer offer1 = createOffer(provider, "Offer Pag 1", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer1, 999L, 0L);
        createEvidence(offer1, "https://stream-pag.com/tarifs1");

        CommercialOffer offer2 = createOffer(provider, "Offer Pag 2", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer2, 1199L, 0L);
        createEvidence(offer2, "https://stream-pag.com/tarifs2");

        // Action 1: sub1 + offer1 (PLANNED)
        String resp1 = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscriptionId", sub1.getId(), "offerId", offer1.getId(),
                                "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                                "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID a1Id = UUID.fromString(objectMapper.readTree(resp1).get("id").asText());

        // Action 2: sub2 + offer2 (COMMITTED)
        String resp2 = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscriptionId", sub2.getId(), "offerId", offer2.getId(),
                                "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                                "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID a2Id = UUID.fromString(objectMapper.readTree(resp2).get("id").asText());
        mockMvc.perform(patch("/v1/savings/actions/" + a2Id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED"))))
                .andExpect(status().isOk());

        // Filtre par statut PLANNED
        mockMvc.perform(get("/v1/savings/actions?status=PLANNED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a1Id.toString()));

        // Filtre par abonnement sub2
        mockMvc.perform(get("/v1/savings/actions?subscriptionId=" + sub2.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a2Id.toString()));

        // Pagination avec page=0 et size=1
        mockMvc.perform(get("/v1/savings/actions?page=0&size=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Résumé financier cohérent par statut, devise et horizon sans économie observée inventée")
    void testSummaryConsistentByStatusCurrencyHorizonAndNoFakeObserved() throws Exception {
        User user = activeUser("summary@eparly.test");

        Subscription sub1 = createSubscription(user, "Sub 1", "P1", 1599L);
        Subscription sub2 = createSubscription(user, "Sub 2", "P2", 1599L);
        Subscription sub3 = createSubscription(user, "Sub 3", "P3", 1599L);
        Subscription sub4 = createSubscription(user, "Sub 4", "P4", 1599L);

        OfferProvider provider = createProvider("StreamSumProvider", OfferSector.STREAMING, "https://stream-sum.com");
        CommercialOffer offer1 = createOffer(provider, "Sum Offer 1", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer1, 999L, 0L); // 600c/mois * 12 = 7200L
        createEvidence(offer1, "https://stream-sum.com/tarifs1");

        CommercialOffer offer2 = createOffer(provider, "Sum Offer 2", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer2, 999L, 0L);
        createEvidence(offer2, "https://stream-sum.com/tarifs2");

        CommercialOffer offer3 = createOffer(provider, "Sum Offer 3", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer3, 999L, 0L);
        createEvidence(offer3, "https://stream-sum.com/tarifs3");

        CommercialOffer offer4 = createOffer(provider, "Sum Offer 4", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer4, 999L, 0L);
        createEvidence(offer4, "https://stream-sum.com/tarifs4");

        // Action 1: PLANNED (7200L)
        mockMvc.perform(post("/v1/savings/actions").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subscriptionId", sub1.getId(), "offerId", offer1.getId(),
                        "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                        "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated());

        // Action 2: COMMITTED (7200L)
        String r2 = mockMvc.perform(post("/v1/savings/actions").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subscriptionId", sub2.getId(), "offerId", offer2.getId(),
                        "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                        "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(patch("/v1/savings/actions/" + objectMapper.readTree(r2).get("id").asText() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED")))).andExpect(status().isOk());

        // Action 3: DECLARED (7200L)
        String r3 = mockMvc.perform(post("/v1/savings/actions").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subscriptionId", sub3.getId(), "offerId", offer3.getId(),
                        "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                        "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID a3Id = UUID.fromString(objectMapper.readTree(r3).get("id").asText());
        mockMvc.perform(patch("/v1/savings/actions/" + a3Id + "/status").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED"))));
        mockMvc.perform(patch("/v1/savings/actions/" + a3Id + "/status").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("status", "DECLARED"))))
                .andExpect(status().isOk());

        // Action 4: CANCELED (7200L)
        String r4 = mockMvc.perform(post("/v1/savings/actions").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subscriptionId", sub4.getId(), "offerId", offer4.getId(),
                        "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                        "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(patch("/v1/savings/actions/" + objectMapper.readTree(r4).get("id").asText() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "CANCELED")))).andExpect(status().isOk());

        // GET /v1/savings/actions/summary
        mockMvc.perform(get("/v1/savings/actions/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionsCount").value(4))
                .andExpect(jsonPath("$.plannedCount").value(1))
                .andExpect(jsonPath("$.committedCount").value(1))
                .andExpect(jsonPath("$.declaredCount").value(1))
                .andExpect(jsonPath("$.canceledCount").value(1))
                // Potentiel total (PLANNED 7200 + COMMITTED 7200 + DECLARED 7200 = 21600; CANCELED exclu!)
                .andExpect(jsonPath("$.potentialSavingsMinor").value(21600))
                .andExpect(jsonPath("$.committedPotentialSavingsMinor").value(7200))
                .andExpect(jsonPath("$.declaredPotentialSavingsMinor").value(7200))
                // observedSavingsMinor doit être null à l'étape 11A (aucune économie observée inventée)
                .andExpect(jsonPath("$.observedSavingsMinor").isEmpty())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.horizonMonths").value(12));
    }

    @Test
    @DisplayName("Sécurité : JWT obligatoire sur les 5 endpoints (401 si absent)")
    void testSecurityJwtRequiredOnAllEndpoints() throws Exception {
        UUID randomId = UUID.randomUUID();

        // 1. POST /v1/savings/actions
        mockMvc.perform(post("/v1/savings/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 2. GET /v1/savings/actions
        mockMvc.perform(get("/v1/savings/actions"))
                .andExpect(status().isUnauthorized());

        // 3. GET /v1/savings/actions/{id}
        mockMvc.perform(get("/v1/savings/actions/" + randomId))
                .andExpect(status().isUnauthorized());

        // 4. PATCH /v1/savings/actions/{id}/status
        mockMvc.perform(patch("/v1/savings/actions/" + randomId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMMITTED\"}"))
                .andExpect(status().isUnauthorized());

        // 5. GET /v1/savings/actions/summary
        mockMvc.perform(get("/v1/savings/actions/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Isolation utilisateur : ressource d'un autre utilisateur renvoie 404 neutre")
    void testIsolationOtherUserResourceReturnsNeutral404() throws Exception {
        User userA = activeUser("usera@eparly.test");
        User userB = activeUser("userb@eparly.test");

        Subscription subA = createSubscription(userA, "Sub User A", "ProviderA", 1599L);
        OfferProvider provider = createProvider("StreamIsoProvider", OfferSector.STREAMING, "https://stream-iso.com");
        CommercialOffer offer = createOffer(provider, "Stream Iso Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-iso.com/tarifs");

        // User A crée une action
        String respA = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscriptionId", subA.getId(), "offerId", offer.getId(),
                                "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                                "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID actionIdA = UUID.fromString(objectMapper.readTree(respA).get("id").asText());

        // User B tente d'accéder à l'action de User A -> 404 Not Found neutre
        mockMvc.perform(get("/v1/savings/actions/" + actionIdA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Action introuvable"));

        // User B tente de modifier le statut de l'action de User A -> 404 Not Found neutre
        mockMvc.perform(patch("/v1/savings/actions/" + actionIdA + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Action introuvable"));

        // User B tente de créer une action en fournissant l'abonnement de User A -> 404 Not Found neutre
        mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscriptionId", subA.getId(), "offerId", offer.getId(),
                                "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                                "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Abonnement introuvable"));
    }

    @Test
    @DisplayName("Intégrité financière : aucune modification des comptes bancaires ni transactions")
    void testFinancialTransactionsUntouched() throws Exception {
        User user = activeUser("notouchfinancial@eparly.test");
        Subscription subscription = createSubscription(user, "Stream No Touch", "ProviderNT", 1599L);

        OfferProvider provider = createProvider("StreamNTProvider", OfferSector.STREAMING, "https://stream-nt.com");
        CommercialOffer offer = createOffer(provider, "Stream NT Offer", OfferSector.STREAMING,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6));
        createPricing(offer, 999L, 0L);
        createEvidence(offer, "https://stream-nt.com/tarifs");

        long accountsCountBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM accounts", Long.class);
        long transactionsCountBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class);

        // Exécuter un cycle complet de l'action
        String createResp = mockMvc.perform(post("/v1/savings/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscriptionId", subscription.getId(), "offerId", offer.getId(),
                                "sector", "STREAMING", "horizonMonths", 12, "requirements", Map.of(),
                                "userKnownCancellationFeeMinor", 0L, "userKnownOtherFeesMinor", 0L))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID actionId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        mockMvc.perform(patch("/v1/savings/actions/" + actionId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMMITTED"))))
                .andExpect(status().isOk());

        long accountsCountAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM accounts", Long.class);
        long transactionsCountAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class);

        // Aucune altération des tables financières
        assertThat(accountsCountAfter).isEqualTo(accountsCountBefore);
        assertThat(transactionsCountAfter).isEqualTo(transactionsCountBefore);
    }
}
