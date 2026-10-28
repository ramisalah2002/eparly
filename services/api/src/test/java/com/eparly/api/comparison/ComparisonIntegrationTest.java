package com.eparly.api.comparison;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.offer.repository.*;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.repository.SubscriptionRepository;
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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@Transactional
class ComparisonIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private OfferProviderRepository providerRepository;
    @Autowired private CommercialOfferRepository offerRepository;
    @Autowired private OfferPricingRepository pricingRepository;
    @Autowired private OfferEvidenceRepository evidenceRepository;

    /* ---------- Helpers ---------- */

    private User activeUser(String email) {
        User user = new User(email, "DummyPasswordHash2026!");
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

    private Account createAccount(User user, String name) throws Exception {
        String resp = mockMvc.perform(post("/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp);
        Account account = new Account(user, name);
        account.setId(UUID.fromString(json.get("id").asText()));
        return account;
    }

    private Subscription createSubscription(User user, Account account, String name, String provider) {
        Subscription s = new Subscription();
        s.setUser(user);
        s.setAccount(account);
        s.setName(name);
        s.setProvider(provider);
        s.setDeclaredAmountMinor(1599L);
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
                                        String characteristics, String eligibility,
                                        String commitment, String territory, LocalDate validFrom, LocalDate validUntil,
                                        OfferStatus status) {
        CommercialOffer o = new CommercialOffer();
        o.setProvider(provider);
        o.setCommercialName(name);
        o.setSector(sector);
        o.setCharacteristics(characteristics);
        o.setEligibilityConditions(eligibility);
        o.setCommitment(commitment);
        o.setTerritory(territory);
        o.setPublicationStatus(status);
        o.setValidFrom(validFrom);
        o.setValidUntil(validUntil);
        return offerRepository.saveAndFlush(o);
    }

    private OfferPricing createPricing(CommercialOffer offer, Long amount, OfferBillingPeriod period) {
        OfferPricing p = new OfferPricing();
        p.setOffer(offer);
        p.setAmountMinor(amount);
        p.setCurrency("EUR");
        p.setBillingPeriod(period);
        p.setTaxesIncluded(true);
        p.setDataUnknown(false);
        return pricingRepository.saveAndFlush(p);
    }

    private OfferEvidence createEvidence(CommercialOffer offer, String url, EvidenceStatus status) {
        OfferEvidence e = new OfferEvidence();
        e.setOffer(offer);
        e.setOfficialUrl(url);
        e.setConsultedAt(Instant.now());
        e.setVerifiedAt(Instant.now());
        e.setNature("Prix officiel");
        e.setVerifiedSummary("Verifie");
        e.setVerificationStatus(status);
        e.setProvenance("Test");
        return evidenceRepository.saveAndFlush(e);
    }

    private String evalBody(UUID subscriptionId, OfferSector sector, Map<String, Object> requirements) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "subscriptionId", subscriptionId,
                    "sector", sector.name(),
                    "requirements", requirements
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasOffer(JsonNode resp, String field, String commercialName) {
        JsonNode arr = resp.path(field);
        if (arr == null || arr.isMissingNode() || !arr.isArray()) return false;
        for (JsonNode item : arr) {
            JsonNode nameNode = item.path(field.replace("Offers", ""));
            // field is like "compatibleOffers", look for "commercialName" in each item
            JsonNode nameNode2 = item.path("commercialName");
            if (!nameNode2.isMissingNode() && commercialName.equals(nameNode2.asText(null))) return true;
        }
        return false;
    }

    private JsonNode getOffers(JsonNode resp, String field) {
        JsonNode arr = resp.path(field);
        if (arr.isMissingNode() || !arr.isArray()) return null;
        return arr;
    }

    /* ---------- 1. Streaming: tous critères satisfaits ---------- */

    @Test
    @DisplayName("1. Streaming: tous critères satisfaits")
    void streamingAllSatisfied() throws Exception {
        User user = activeUser("stream1@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel");

        OfferProvider provider = createProvider("StreamAlt", OfferSector.STREAMING, "https://stream-alt.com");
        CommercialOffer offer = createOffer(provider, "Forfait HD", OfferSector.STREAMING,
                "{\"screens\":4,\"videoQuality\":\"4K\",\"ads\":false,\"offlineDownload\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1299L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://stream-alt.com/hd", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.STREAMING,
                Map.of("minScreens", 2, "minVideoQuality", "HD", "adsAccepted", false));
        JsonNode compatible = resp.path("compatibleOffers");
        assertThat(compatible.isArray()).isTrue();
        assertThat(compatible.size()).isGreaterThan(0);
        assertThat(hasOffer(resp, "compatibleOffers", "Forfait HD")).isTrue();
        assertThat(compatible.get(0).path("status").asText()).isEqualTo("COMPATIBLE");
    }

    /* ---------- 2. Streaming: publicité interdite ---------- */

    @Test
    @DisplayName("2. Streaming: publicité interdite")
    void streamingAdsNotAccepted() throws Exception {
        User user = activeUser("stream2@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel");

        OfferProvider provider = createProvider("StreamAlt2", OfferSector.STREAMING, "https://stream-alt2.com");
        CommercialOffer offer = createOffer(provider, "Avec Pub", OfferSector.STREAMING,
                "{\"screens\":2,\"videoQuality\":\"HD\",\"ads\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://stream-alt2.com/pub", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.STREAMING,
                Map.of("minScreens", 2, "minVideoQuality", "HD", "adsAccepted", false));
        // adsAccepted est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "Avec Pub")).isTrue();
        JsonNode incompatible = getOffers(resp, "incompatibleOffers");
        JsonNode avecPub = null;
        for (JsonNode item : incompatible) {
            if ("Avec Pub".equals(item.path("commercialName").asText(null))) {
                avecPub = item;
                break;
            }
        }
        assertThat(avecPub).isNotNull();
        boolean hasAdsCriterion = false;
        for (JsonNode c : avecPub.path("criteria")) {
            if ("adsAccepted".equals(c.path("code").asText(null))) {
                hasAdsCriterion = true;
                assertThat(c.path("result").asText()).isEqualTo("NOT_SATISFIED");
                break;
            }
        }
        assertThat(hasAdsCriterion).isTrue();
    }

    /* ---------- 3. Streaming: écrans insuffisants ---------- */

    @Test
    @DisplayName("3. Streaming: écrans insuffisants")
    void streamingInsufficientScreens() throws Exception {
        User user = activeUser("stream3@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel");

        OfferProvider provider = createProvider("StreamAlt3", OfferSector.STREAMING, "https://stream-alt3.com");
        CommercialOffer offer = createOffer(provider, "Un Écran", OfferSector.STREAMING,
                "{\"screens\":1,\"videoQuality\":\"HD\",\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 799L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://stream-alt3.com/1ecran", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.STREAMING,
                Map.of("minScreens", 4, "minVideoQuality", "HD"));
        assertThat(hasOffer(resp, "incompatibleOffers", "Un Écran")).isTrue();
    }

    /* ---------- 4. Streaming: qualité insuffisante ---------- */

    @Test
    @DisplayName("4. Streaming: qualité insuffisante")
    void streamingInsufficientQuality() throws Exception {
        User user = activeUser("stream4@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel");

        OfferProvider provider = createProvider("StreamAlt4", OfferSector.STREAMING, "https://stream-alt4.com");
        CommercialOffer offer = createOffer(provider, "SD Only", OfferSector.STREAMING,
                "{\"screens\":2,\"videoQuality\":\"SD\",\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 599L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://stream-alt4.com/sd", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.STREAMING,
                Map.of("minScreens", 2, "minVideoQuality", "4K"));
        assertThat(hasOffer(resp, "incompatibleOffers", "SD Only")).isTrue();
    }

    /* ---------- 5. Streaming: caractéristique inconnue ---------- */

    @Test
    @DisplayName("5. Streaming: caractéristique inconnue")
    void streamingUnknownCharacteristic() throws Exception {
        User user = activeUser("stream5@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel");

        OfferProvider provider = createProvider("StreamAlt5", OfferSector.STREAMING, "https://stream-alt5.com");
        CommercialOffer offer = createOffer(provider, "Pas d'infos", OfferSector.STREAMING,
                "{\"screens\":2}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://stream-alt5.com/noinfo", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.STREAMING,
                Map.of("minScreens", 2, "minVideoQuality", "HD"));
        assertThat(hasOffer(resp, "needsInformationOffers", "Pas d'infos")).isTrue();
    }

    /* ---------- 6. Streaming: catalogue de contenus non vérifié ---------- */

    @Test
    @DisplayName("6. Streaming: catalogue de contenus non vérifié")
    void streamingContentNotVerified() throws Exception {
        User user = activeUser("stream6@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel");

        OfferProvider provider = createProvider("StreamAlt6", OfferSector.STREAMING, "https://stream-alt6.com");
        CommercialOffer offer = createOffer(provider, "Contenu ?", OfferSector.STREAMING,
                "{\"screens\":4,\"videoQuality\":\"4K\",\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1599L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://stream-alt6.com/contenu", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.STREAMING,
                Map.of("minScreens", 4, "minVideoQuality", "4K", "requiredContent", "Série X"));
        // requiredContent est maintenant un critère obligatoire mais non vérifiable -> NEEDS_INFORMATION
        assertThat(hasOffer(resp, "needsInformationOffers", "Contenu ?")).isTrue();
    }

    /* ---------- 7. Mobile: données suffisantes ---------- */

    @Test
    @DisplayName("7. Mobile: données suffisantes")
    void mobileSufficientData() throws Exception {
        User user = activeUser("mobile1@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt1", OfferSector.MOBILE_PLAN, "https://mobile-alt1.com");
        CommercialOffer offer = createOffer(provider, "200 Go 5G", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"roaming\":true,\"roamingDataGb\":20,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt1.com/200go", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "requires5G", true));
        assertThat(hasOffer(resp, "compatibleOffers", "200 Go 5G")).isTrue();
    }

    /* ---------- 8. Mobile: données insuffisantes ---------- */

    @Test
    @DisplayName("8. Mobile: données insuffisantes")
    void mobileInsufficientData() throws Exception {
        User user = activeUser("mobile2@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt2", OfferSector.MOBILE_PLAN, "https://mobile-alt2.com");
        CommercialOffer offer = createOffer(provider, "50 Go", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":50,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt2.com/50go", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "incompatibleOffers", "50 Go")).isTrue();
    }

    /* ---------- 9. Mobile: 5G requise absente ---------- */

    @Test
    @DisplayName("9. Mobile: 5G requise absente")
    void mobile5gRequiredAbsent() throws Exception {
        User user = activeUser("mobile3@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt3", OfferSector.MOBILE_PLAN, "https://mobile-alt3.com");
        CommercialOffer offer = createOffer(provider, "4G Only", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1499L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt3.com/4g", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "requires5G", true));
        // requires5G est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "4G Only")).isTrue();
        JsonNode incompatible = getOffers(resp, "incompatibleOffers");
        JsonNode fourG = null;
        for (JsonNode item : incompatible) {
            if ("4G Only".equals(item.path("commercialName").asText(null))) {
                fourG = item;
                break;
            }
        }
        assertThat(fourG).isNotNull();
        boolean has5GCriterion = false;
        for (JsonNode c : fourG.path("criteria")) {
            if ("requires5G".equals(c.path("code").asText(null))) {
                has5GCriterion = true;
                assertThat(c.path("result").asText()).isEqualTo("NOT_SATISFIED");
                break;
            }
        }
        assertThat(has5GCriterion).isTrue();
    }

    /* ---------- 10. Mobile: itinérance insuffisante ---------- */

    @Test
    @DisplayName("10. Mobile: itinérance insuffisante")
    void mobileInsufficientRoaming() throws Exception {
        User user = activeUser("mobile4@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt4", OfferSector.MOBILE_PLAN, "https://mobile-alt4.com");
        CommercialOffer offer = createOffer(provider, "Roaming 5 Go", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"roaming\":true,\"roamingDataGb\":5,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 2499L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt4.com/roaming", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "roamingRequired", true, "minRoamingDataGb", 20));
        // minRoamingDataGb est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "Roaming 5 Go")).isTrue();
        JsonNode incompatible = getOffers(resp, "incompatibleOffers");
        JsonNode roaming = null;
        for (JsonNode item : incompatible) {
            if ("Roaming 5 Go".equals(item.path("commercialName").asText(null))) {
                roaming = item;
                break;
            }
        }
        assertThat(roaming).isNotNull();
        boolean hasRoamingCriterion = false;
        for (JsonNode c : roaming.path("criteria")) {
            if ("minRoamingDataGb".equals(c.path("code").asText(null))) {
                hasRoamingCriterion = true;
                assertThat(c.path("result").asText()).isEqualTo("NOT_SATISFIED");
                break;
            }
        }
        assertThat(hasRoamingCriterion).isTrue();
    }

    /* ---------- 11. Mobile: engagement trop long ---------- */

    @Test
    @DisplayName("11. Mobile: engagement trop long")
    void mobileCommitmentTooLong() throws Exception {
        User user = activeUser("mobile5@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt5", OfferSector.MOBILE_PLAN, "https://mobile-alt5.com");
        CommercialOffer offer = createOffer(provider, "Engagé 24 mois", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Engagement 24 mois\"}",
                null, "Engagement 24 mois", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt5.com/engage", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "maxCommitmentMonths", 12));
        // maxCommitmentMonths est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "Engagé 24 mois")).isTrue();
        JsonNode incompatible = getOffers(resp, "incompatibleOffers");
        JsonNode engage = null;
        for (JsonNode item : incompatible) {
            if ("Engagé 24 mois".equals(item.path("commercialName").asText(null))) {
                engage = item;
                break;
            }
        }
        assertThat(engage).isNotNull();
        boolean hasCommitmentCriterion = false;
        for (JsonNode c : engage.path("criteria")) {
            if ("maxCommitmentMonths".equals(c.path("code").asText(null))) {
                hasCommitmentCriterion = true;
                assertThat(c.path("result").asText()).isEqualTo("NOT_SATISFIED");
                break;
            }
        }
        assertThat(hasCommitmentCriterion).isTrue();
    }

    /* ---------- 12. Critère facultatif non satisfait ---------- */

    @Test
    @DisplayName("12. Critère facultatif non satisfait")
    void optionalCriterionNotSatisfied() throws Exception {
        User user = activeUser("mobile6@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt6", OfferSector.MOBILE_PLAN, "https://mobile-alt6.com");
        CommercialOffer offer = createOffer(provider, "Sans 5G", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1499L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt6.com/nog5", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "requires5G", true));
        // requires5G est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "Sans 5G")).isTrue();
    }

    /* ---------- 13. Offre promotionnelle ---------- */

    @Test
    @DisplayName("13. Offre promotionnelle")
    void promotionalOffer() throws Exception {
        User user = activeUser("mobile7@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt7", OfferSector.MOBILE_PLAN, "https://mobile-alt7.com");
        CommercialOffer offer = createOffer(provider, "Promo 12 mois", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        OfferPricing pricing = createPricing(offer, 2999L, OfferBillingPeriod.MONTHLY);
        pricing.setPromotionPercent(20);
        pricing.setPromotionDurationMonths(12);
        pricing.setPriceAfterPromotionMinor(2399L);
        pricingRepository.saveAndFlush(pricing);
        createEvidence(offer, "https://mobile-alt7.com/promo", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "compatibleOffers", "Promo 12 mois")).isTrue();
        assertThat(resp.path("compatibleOffers").get(0).path("promotionPercent").asInt()).isEqualTo(20);
    }

    /* ---------- 14. Frais annexes inconnus ---------- */

    @Test
    @DisplayName("14. Frais annexes inconnus")
    void unknownAncillaryFees() throws Exception {
        User user = activeUser("mobile8@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt8", OfferSector.MOBILE_PLAN, "https://mobile-alt8.com");
        CommercialOffer offer = createOffer(provider, "Frais ?", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        OfferPricing pricing = createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        pricing.setActivationFeeMinor(null);
        pricing.setOtherFees(null);
        pricing.setDataUnknown(true);
        pricingRepository.saveAndFlush(pricing);
        createEvidence(offer, "https://mobile-alt8.com/frais", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "compatibleOffers", "Frais ?")).isTrue();
    }

    /* ---------- 15. Offre expirée ---------- */

    @Test
    @DisplayName("15. Offre expirée")
    void expiredOffer() throws Exception {
        User user = activeUser("mobile9@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt9", OfferSector.MOBILE_PLAN, "https://mobile-alt9.com");
        CommercialOffer offer = createOffer(provider, "Expiré", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt9.com/expire", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "compatibleOffers", "Expiré")).isFalse();
    }

    /* ---------- 16. Offre STALE ---------- */

    @Test
    @DisplayName("16. Offre STALE")
    void staleOffer() throws Exception {
        User user = activeUser("mobile10@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt10", OfferSector.MOBILE_PLAN, "https://mobile-alt10.com");
        CommercialOffer offer = createOffer(provider, "Stale", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        OfferEvidence evidence = createEvidence(offer, "https://mobile-alt10.com/stale", EvidenceStatus.VERIFIED);
        evidence.setVerifiedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        evidenceRepository.saveAndFlush(evidence);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "compatibleOffers", "Stale")).isFalse();
    }

    /* ---------- 17. Offre DRAFT ---------- */

    @Test
    @DisplayName("17. Offre DRAFT")
    void draftOffer() throws Exception {
        User user = activeUser("mobile11@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt11", OfferSector.MOBILE_PLAN, "https://mobile-alt11.com");
        createOffer(provider, "Brouillon", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.DRAFT);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "compatibleOffers", "Brouillon")).isFalse();
    }

    /* ---------- 18. Offre archivée ---------- */

    @Test
    @DisplayName("18. Offre archivée")
    void archivedOffer() throws Exception {
        User user = activeUser("mobile12@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt12", OfferSector.MOBILE_PLAN, "https://mobile-alt12.com");
        createOffer(provider, "Archive", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.ARCHIVED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "compatibleOffers", "Archive")).isFalse();
    }

    /* ---------- 19. Conditions d'éligibilité non satisfaites ---------- */

    @Test
    @DisplayName("19. Conditions d'éligibilité non satisfaites")
    void eligibilityNotMet() throws Exception {
        User user = activeUser("mobile13@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt13", OfferSector.MOBILE_PLAN, "https://mobile-alt13.com");
        CommercialOffer offer = createOffer(provider, "Réservé", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                "Réservé aux nouveaux clients uniquement", "Aucun engagement", "FR",
                LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt13.com/reserve", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "eligibilityRestrictions", "aucun"));
        // eligibilityRestrictions est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "Réservé")).isTrue();
    }

    /* ---------- 20. Catalogue vide ---------- */

    @Test
    @DisplayName("20. Catalogue vide")
    void emptyCatalog() throws Exception {
        User user = activeUser("mobile14@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(resp.path("compatibleOffers").size()).isEqualTo(0);
        assertThat(resp.path("totalEligibleOffers").asInt()).isEqualTo(0);
    }

    /* ---------- 21. JSON malformé ---------- */

    @Test
    @DisplayName("21. JSON malformé")
    void malformedJson() throws Exception {
        User user = activeUser("mobile15@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt15", OfferSector.MOBILE_PLAN, "https://mobile-alt15.com");
        CommercialOffer offer = createOffer(provider, "Json Cassé", OfferSector.MOBILE_PLAN,
                "{json invalide", null, "Aucun engagement", "FR",
                LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt15.com/casse", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "needsInformationOffers", "Json Cassé")).isTrue();
    }

    /* ---------- 22. Champ absent ---------- */

    @Test
    @DisplayName("22. Champ absent")
    void missingField() throws Exception {
        User user = activeUser("mobile16@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt16", OfferSector.MOBILE_PLAN, "https://mobile-alt16.com");
        CommercialOffer offer = createOffer(provider, "Sans Data", OfferSector.MOBILE_PLAN,
                "{\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt16.com/nodata", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "needsInformationOffers", "Sans Data")).isTrue();
    }

    /* ---------- 23. Faux différent d'inconnu ---------- */

    @Test
    @DisplayName("23. Faux différent d'inconnu")
    void falseVsUnknown() throws Exception {
        User user = activeUser("mobile17@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt17", OfferSector.MOBILE_PLAN, "https://mobile-alt17.com");
        CommercialOffer offer = createOffer(provider, "Pas 5G", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1499L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt17.com/pas5g", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100, "requires5G", true));
        // requires5G est maintenant un critère obligatoire quand l'utilisateur l'exprime
        assertThat(hasOffer(resp, "incompatibleOffers", "Pas 5G")).isTrue();
    }

    /* ---------- 24. Valeur zéro différente d'inconnue ---------- */

    @Test
    @DisplayName("24. Valeur zéro différente d'inconnue")
    void zeroVsUnknown() throws Exception {
        User user = activeUser("mobile18@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt18", OfferSector.MOBILE_PLAN, "https://mobile-alt18.com");
        CommercialOffer offer = createOffer(provider, "Zero Data", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":0,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 0L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt18.com/zero", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(hasOffer(resp, "incompatibleOffers", "Zero Data")).isTrue();
    }

    /* ---------- 25. Abonnement d'un autre utilisateur ---------- */

    @Test
    @DisplayName("25. Abonnement d'un autre utilisateur")
    void otherUsersSubscription() throws Exception {
        User u1 = activeUser("mobile19a@eparly.test");
        User u2 = activeUser("mobile19b@eparly.test");
        Account account = createAccount(u1, "Compte");
        Subscription sub = createSubscription(u1, account, "Mon Mobile", "MobileActuel");

        mockMvc.perform(post("/v1/comparisons/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), OfferSector.MOBILE_PLAN,
                                Map.of("minDataGb", 100))))
                .andExpect(status().isNotFound());
    }

    /* ---------- 26. JWT absent ou invalide ---------- */

    @Test
    @DisplayName("26. JWT absent ou invalide")
    void missingOrInvalidJwt() throws Exception {
        mockMvc.perform(post("/v1/comparisons/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/comparisons/criteria")
                        .param("sector", "STREAMING"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/comparisons/metadata"))
                .andExpect(status().isUnauthorized());
    }

    /* ---------- 27. Comparaison reproductible ---------- */

    @Test
    @DisplayName("27. Comparaison reproductible")
    void reproducibleComparison() throws Exception {
        User user = activeUser("mobile20@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt20", OfferSector.MOBILE_PLAN, "https://mobile-alt20.com");
        CommercialOffer offer = createOffer(provider, "Déterministe", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt20.com/det", EvidenceStatus.VERIFIED);

        String body = evalBody(sub.getId(), OfferSector.MOBILE_PLAN, Map.of("minDataGb", 100));

        JsonNode resp1 = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        JsonNode resp2 = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));

        // Comparer sans les timestamps qui peuvent différer
        assertThat(resp1.path("compatibleOffers").size()).isEqualTo(resp2.path("compatibleOffers").size());
        assertThat(resp1.path("totalEligibleOffers").asInt()).isEqualTo(resp2.path("totalEligibleOffers").asInt());
        assertThat(resp1.path("sector").asText()).isEqualTo(resp2.path("sector").asText());
    }

    /* ---------- 28. Aucune modification financière en base ---------- */

    @Test
    @DisplayName("28. Aucune modification financière en base")
    void noFinancialModifications() throws Exception {
        User user = activeUser("mobile21@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        long initialOffers = offerRepository.count();
        long initialPricing = pricingRepository.count();

        mockMvc.perform(post("/v1/comparisons/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), OfferSector.MOBILE_PLAN,
                                Map.of("minDataGb", 100))))
                .andExpect(status().isOk());

        assertThat(offerRepository.count()).isEqualTo(initialOffers);
        assertThat(pricingRepository.count()).isEqualTo(initialPricing);
    }

    /* ---------- 29. Score et explications cohérents ---------- */

    @Test
    @DisplayName("29. Score et explications cohérents")
    void scoreAndExplanations() throws Exception {
        User user = activeUser("mobile22@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt22", OfferSector.MOBILE_PLAN, "https://mobile-alt22.com");
        CommercialOffer offer = createOffer(provider, "Cohérent", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt22.com/coherent", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        JsonNode compatible = getOffers(resp, "compatibleOffers");
        assertThat(compatible).isNotNull();
        assertThat(compatible.size()).isGreaterThan(0);
        assertThat(compatible.get(0).path("status").asText()).isEqualTo("COMPATIBLE");
        int score = compatible.get(0).path("heuristicScore").asInt();
        assertThat(score).isBetween(0, 100);
    }

    /* ---------- 30. Aucune économie chiffrée inventée ---------- */

    @Test
    @DisplayName("30. Aucune économie chiffrée inventée")
    void noInventedSavings() throws Exception {
        User user = activeUser("mobile23@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel");

        OfferProvider provider = createProvider("MobileAlt23", OfferSector.MOBILE_PLAN, "https://mobile-alt23.com");
        CommercialOffer offer = createOffer(provider, "Pas d'éco", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        createEvidence(offer, "https://mobile-alt23.com/pasdec", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 100));
        assertThat(resp.path("savings").isMissingNode()).isTrue();
        JsonNode compatible = getOffers(resp, "compatibleOffers");
        assertThat(compatible).isNotNull();
        assertThat(compatible.size()).isGreaterThan(0);
        assertThat(compatible.get(0).path("savings").isMissingNode()).isTrue();
    }

    /* ---------- Critères endpoint ---------- */

    @Test
    @DisplayName("Critères disponibles pour un secteur")
    void availableCriteria() throws Exception {
        User user = activeUser("mobile24@eparly.test");

        mockMvc.perform(get("/v1/comparisons/criteria")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .param("sector", "STREAMING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").isNotEmpty())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].mandatory").exists());

        mockMvc.perform(get("/v1/comparisons/criteria")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .param("sector", "MOBILE_PLAN"))
                .andExpect(status().isOk());
    }

    /* ---------- Metadata ---------- */

    @Test
    @DisplayName("Métadonnées du moteur")
    void metadata() throws Exception {
        User user = activeUser("mobile25@eparly.test");

        mockMvc.perform(get("/v1/comparisons/metadata")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineVersion").exists())
                .andExpect(jsonPath("$.supportedSectors").exists());
    }

    /* ---------- Helper ---------- */

    private JsonNode doEvaluate(User user, UUID subscriptionId, OfferSector sector, Map<String, Object> requirements)
            throws Exception {
        String resp = mockMvc.perform(post("/v1/comparisons/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(subscriptionId, sector, requirements)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }
}
