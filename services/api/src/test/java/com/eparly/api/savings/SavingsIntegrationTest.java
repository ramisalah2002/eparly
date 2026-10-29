package com.eparly.api.savings;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.offer.domain.*;
import com.eparly.api.offer.repository.*;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.repository.RecurringSeriesRepository;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.subscription.domain.SubscriptionOrigin;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.repository.SubscriptionRepository;
import com.eparly.api.savings.dto.SavingsEstimateResponse;
import com.eparly.api.savings.domain.SavingsStatus;
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
class SavingsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private RecurringSeriesRepository recurringSeriesRepository;
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

    private Subscription createSubscription(User user, Account account, String name, String provider,
                                            Long declaredAmount, RecurringFrequency frequency,
                                            SubscriptionStatus status) {
        Subscription s = new Subscription();
        s.setUser(user);
        s.setAccount(account);
        s.setName(name);
        s.setProvider(provider);
        s.setDeclaredAmountMinor(declaredAmount);
        s.setCurrency("EUR");
        s.setDeclaredFrequency(frequency != null ? frequency : RecurringFrequency.MONTHLY);
        s.setDeclaredStatus(status);
        s.setOrigin(SubscriptionOrigin.USER_DECLARED);
        return subscriptionRepository.saveAndFlush(s);
    }

    private RecurringSeries createRecurringSeries(Account account, String label, Long usualAmountMinor,
                                                  RecurringFrequency frequency) {
        RecurringSeries series = new RecurringSeries();
        series.setAccount(account);
        series.setNormalizedLabel(label);
        series.setCurrency("EUR");
        series.setFrequency(frequency != null ? frequency : RecurringFrequency.MONTHLY);
        series.setStatus(RecurringSeriesStatus.CONFIRMED);
        series.setUsualAmountMinor(usualAmountMinor);
        series.setMinAmountMinor(usualAmountMinor);
        series.setMaxAmountMinor(usualAmountMinor);
        series.setOccurrenceCount(3);
        series.setFirstOccurrenceDate(LocalDate.now().minusMonths(3));
        series.setLastOccurrenceDate(LocalDate.now());
        series.setNextEstimatedDate(LocalDate.now().plusMonths(1));
        series.setMonthlyEquivalentMinor(java.math.BigDecimal.valueOf(usualAmountMinor));
        series.setConfidenceScore(90);
        series.setLastAnalyzedAt(Instant.now());
        return recurringSeriesRepository.saveAndFlush(series);
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

    private OfferPricing createPricing(CommercialOffer offer, Long amount, OfferBillingPeriod period,
                                       Long activationFee, Long priceAfterPromo, Integer promoPercent,
                                       Integer promoDurationMonths) {
        OfferPricing p = new OfferPricing();
        p.setOffer(offer);
        p.setAmountMinor(amount);
        p.setCurrency("EUR");
        p.setBillingPeriod(period);
        // 0L = aucun frais d'activation connu (valeur connue, pas inconnue)
        p.setActivationFeeMinor(activationFee != null ? activationFee : 0L);
        p.setPriceAfterPromotionMinor(priceAfterPromo);
        p.setPromotionPercent(promoPercent);
        p.setPromotionDurationMonths(promoDurationMonths);
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

    private String evalBody(UUID subscriptionId, UUID offerId, OfferSector sector, Integer horizonMonths,
                            Map<String, Object> requirements, Long cancellationFee, Long otherFees) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("subscriptionId", subscriptionId);
            body.put("offerId", offerId);
            body.put("sector", sector.name());
            body.put("horizonMonths", horizonMonths != null ? horizonMonths : 12);
            body.put("requirements", requirements != null ? requirements : Map.of());
            // Valeur par défaut 0L (aucuns frais déclarés par l'utilisateur)
            // Pour tester des frais inconnus, passer explicitement null
            body.put("userKnownCancellationFeeMinor", cancellationFee != null ? cancellationFee : 0L);
            body.put("userKnownOtherFeesMinor", otherFees != null ? otherFees : 0L);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode doEvaluate(User user, UUID subscriptionId, UUID offerId, OfferSector sector,
                                Integer horizonMonths, Map<String, Object> requirements,
                                Long cancellationFee, Long otherFees) throws Exception {
        String resp = mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(subscriptionId, offerId, sector, horizonMonths, requirements, cancellationFee, otherFees)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    /* ---------- 1. Abonnement mensuel stable ---------- */

    @Test
    @DisplayName("1. Abonnement mensuel stable - économie possible")
    void monthlyStableSavingsPossible() throws Exception {
        User user = activeUser("save1@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Streaming", "StreamActuel", 1599L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("StreamAlt", OfferSector.STREAMING, "https://stream-alt.com");
        CommercialOffer offer = createOffer(provider, "Forfait Moins Cher", OfferSector.STREAMING,
                "{\"screens\":2,\"videoQuality\":\"HD\",\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://stream-alt.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.STREAMING,
                12, Map.of("minScreens", 2, "minVideoQuality", "HD"), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("SAVINGS_POSSIBLE");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isGreaterThan(0);
    }

    /* ---------- 2. Abonnement annuel ---------- */

    @Test
    @DisplayName("2. Abonnement annuel")
    void yearlySubscription() throws Exception {
        User user = activeUser("save2@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Annuel", "StreamActuel", 9999L,
                RecurringFrequency.YEARLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("StreamAlt2", OfferSector.STREAMING, "https://stream-alt2.com");
        CommercialOffer offer = createOffer(provider, "Annuel Alt", OfferSector.STREAMING,
                "{\"screens\":4,\"videoQuality\":\"4K\",\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 7999L, OfferBillingPeriod.YEARLY, null, null, null, null);
        createEvidence(offer, "https://stream-alt2.com/annuel", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.STREAMING,
                12, Map.of("minScreens", 4, "minVideoQuality", "4K"), null, null);
        assertThat(resp.path("status").asText()).isIn(List.of("SAVINGS_POSSIBLE", "NO_SAVINGS"));
    }

    /* ---------- 3. Alternative moins chère ---------- */

    @Test
    @DisplayName("3. Alternative moins chère - 19.99 vs 9.99 avec 15 EUR frais")
    void cheaperAlternativeExample() throws Exception {
        // Abonnement actuel : 19,99 €/mois
        User user = activeUser("save3@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        // Alternative : 9,99 €/mois, frais de changement 15 € (uniquement frais de résiliation)
        OfferProvider provider = createProvider("MobileAlt", OfferSector.MOBILE_PLAN, "https://mobile-alt.com");
        CommercialOffer offer = createOffer(provider, "Forfait 9,99", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt.com/999", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), 1500L, 0L);

        // 19.99 * 12 = 239.88, 9.99 * 12 = 119.88, 239.88 - 119.88 - 15.00 = 105.00
        assertThat(resp.path("status").asText()).isEqualTo("SAVINGS_POSSIBLE");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(10500L);
        assertThat(resp.path("currentTotalCostMinor").asLong()).isEqualTo(23988L);
        assertThat(resp.path("alternativeTotalCostMinor").asLong()).isEqualTo(11988L);
        assertThat(resp.path("totalSwitchingCostMinor").asLong()).isEqualTo(1500L);
    }

    /* ---------- 4. Alternative plus chère ---------- */

    @Test
    @DisplayName("4. Alternative plus chère - pas d'économie")
    void moreExpensiveAlternative() throws Exception {
        User user = activeUser("save4@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt4", OfferSector.MOBILE_PLAN, "https://mobile-alt4.com");
        CommercialOffer offer = createOffer(provider, "Forfait Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":500,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 2999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt4.com/cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("NO_SAVINGS");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isLessThanOrEqualTo(0L);
    }

    /* ---------- 5. Économie nulle ---------- */

    @Test
    @DisplayName("5. Économie nulle - même prix")
    void zeroSavings() throws Exception {
        User user = activeUser("save5@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1499L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt5", OfferSector.MOBILE_PLAN, "https://mobile-alt5.com");
        CommercialOffer offer = createOffer(provider, "Meme Prix", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":100,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1499L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt5.com/meme", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("NO_SAVINGS");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(0);
    }

    /* ---------- 6. Promotion trois mois puis prix normal ---------- */

    @Test
    @DisplayName("6. Promotion 3 mois puis prix normal")
    void promotionThreeMonths() throws Exception {
        User user = activeUser("save6@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt6", OfferSector.MOBILE_PLAN, "https://mobile-alt6.com");
        CommercialOffer offer = createOffer(provider, "Promo 3 Mois", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        // Prix normal 1999, promo -50% pendant 3 mois => 999 pendant 3 mois, puis 1999
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY, null, 1999L, 50, 3);
        createEvidence(offer, "https://mobile-alt6.com/promo", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        // 3 mois à 1000 (1999 * 50% = 999.5, arrondi à 1000) + 9 mois à 1999 = 3000 + 17991 = 20991
        // Actuel: 1999 * 12 = 23988
        // Économie: 23988 - 20991 = 2997
        assertThat(resp.path("status").asText()).isEqualTo("SAVINGS_POSSIBLE");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(2997L);
    }

    /* ---------- 7. Promotion expirée ---------- */

    @Test
    @DisplayName("7. Promotion expirée - pas de prix promotionnel appliqué")
    void expiredPromotion() throws Exception {
        User user = activeUser("save7@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt7", OfferSector.MOBILE_PLAN, "https://mobile-alt7.com");
        CommercialOffer offer = createOffer(provider, "Promo Expirée", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY, null, 1999L, 50, 0);
        createEvidence(offer, "https://mobile-alt7.com/promo-expiree", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        // Même prix normal, pas de promotion active => économie nulle
        assertThat(resp.path("status").asText()).isEqualTo("NO_SAVINGS");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(0);
    }

    /* ---------- 8. Frais d'activation ---------- */

    @Test
    @DisplayName("8. Frais d'activation inclus")
    void activationFeeIncluded() throws Exception {
        User user = activeUser("save8@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt8", OfferSector.MOBILE_PLAN, "https://mobile-alt8.com");
        CommercialOffer offer = createOffer(provider, "Avec Activation", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, 5000L, null, null, null);
        createEvidence(offer, "https://mobile-alt8.com/activation", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        // 1999*12 - 999*12 - 5000 = 23988 - 11988 - 5000 = 7000
        assertThat(resp.path("status").asText()).isEqualTo("SAVINGS_POSSIBLE");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(7000L);
        assertThat(resp.path("activationFeeMinor").asLong()).isEqualTo(5000L);
    }

    /* ---------- 9. Frais de résiliation ---------- */

    @Test
    @DisplayName("9. Frais de résiliation fournis par l'utilisateur")
    void cancellationFeeUserProvided() throws Exception {
        User user = activeUser("save9@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt9", OfferSector.MOBILE_PLAN, "https://mobile-alt9.com");
        CommercialOffer offer = createOffer(provider, "Sans Activation", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt9.com/sans-activation", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), 3000L, null);
        // 1999*12 - 999*12 - 3000 = 23988 - 11988 - 3000 = 9000
        assertThat(resp.path("status").asText()).isEqualTo("SAVINGS_POSSIBLE");
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(9000L);
        assertThat(resp.path("cancellationFeeMinor").asLong()).isEqualTo(3000L);
    }

    /* ---------- 10. Frais inconnus ---------- */

    @Test
    @DisplayName("10. Frais inconnus - INSUFFICIENT_DATA")
    void unknownFees() throws Exception {
        User user = activeUser("save10@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt10", OfferSector.MOBILE_PLAN, "https://mobile-alt10.com");
        CommercialOffer offer = createOffer(provider, "Frais Inconnus", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        // Pas d'activation fee, pas de frais user-known => frais inconnus
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt10.com/frais-inconnus", EvidenceStatus.VERIFIED);

        // Envoyer explicitement null pour les frais (inconnus, pas zéro)
        JsonNode resp;
        String rawBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("subscriptionId", sub.getId());
            put("offerId", offer.getId());
            put("sector", "MOBILE_PLAN");
            put("horizonMonths", 12);
            put("requirements", Map.of("minDataGb", 100, "requires5G", true));
            put("userKnownCancellationFeeMinor", null);
            put("userKnownOtherFeesMinor", null);
        }});
        resp = objectMapper.readTree(mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(resp.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    /* ---------- 11. Taxe incluse ---------- */

    @Test
    @DisplayName("11. Taxe incluse")
    void taxesIncluded() throws Exception {
        User user = activeUser("save11@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt11", OfferSector.MOBILE_PLAN, "https://mobile-alt11.com");
        CommercialOffer offer = createOffer(provider, "TTC", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        OfferPricing pricing = createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        pricing.setTaxesIncluded(true);
        pricingRepository.saveAndFlush(pricing);
        createEvidence(offer, "https://mobile-alt11.com/ttc", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("SAVINGS_POSSIBLE");
    }

    /* ---------- 12. Coût actuel déclaré ---------- */

    @Test
    @DisplayName("12. Coût actuel déclaré")
    void currentCostDeclared() throws Exception {
        User user = activeUser("save12@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt12", OfferSector.MOBILE_PLAN, "https://mobile-alt12.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt12.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        assertThat(resp.path("currentCostSource").asText()).isEqualTo("USER_DECLARED");
        assertThat(resp.path("currentMonthlyCostMinor").asLong()).isEqualTo(1999L);
    }

    /* ---------- 13. Coût actuel observé ---------- */

    @Test
    @DisplayName("13. Coût actuel observé")
    void currentCostObserved() throws Exception {
        User user = activeUser("save13@eparly.test");
        Account account = createAccount(user, "Compte");
        // Pas de montant déclaré, seulement observé via série récurrente
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", null,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);
        RecurringSeries series = createRecurringSeries(account, "MOBILE OPERATOR", 1499L, RecurringFrequency.MONTHLY);
        sub.setRecurringSeries(series);
        subscriptionRepository.saveAndFlush(sub);

        OfferProvider provider = createProvider("MobileAlt13", OfferSector.MOBILE_PLAN, "https://mobile-alt13.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt13.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        assertThat(resp.path("currentCostSource").asText()).isEqualTo("TRANSACTION_OBSERVED");
        assertThat(resp.path("currentMonthlyCostMinor").asLong()).isEqualTo(1499L);
    }

    /* ---------- 14. Absence de coût actuel ---------- */

    @Test
    @DisplayName("14. Absence de coût actuel - INSUFFICIENT_DATA")
    void noCurrentCost() throws Exception {
        User user = activeUser("save14@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", null,
                RecurringFrequency.UNKNOWN, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt14", OfferSector.MOBILE_PLAN, "https://mobile-alt14.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt14.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    /* ---------- 15. Conversion de périodicité ---------- */

    @Test
    @DisplayName("15. Conversion de périodicité - mensuel vs annuel")
    void frequencyConversion() throws Exception {
        User user = activeUser("save15@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Annuel", "StreamActuel", 9999L,
                RecurringFrequency.YEARLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("StreamAlt15", OfferSector.STREAMING, "https://stream-alt15.com");
        CommercialOffer offer = createOffer(provider, "Mensuel Alt", OfferSector.STREAMING,
                "{\"screens\":2,\"videoQuality\":\"HD\",\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://stream-alt15.com/mensuel", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.STREAMING,
                12, Map.of("minScreens", 2, "minVideoQuality", "HD"), null, null);
        // 9999 (annuel) vs 999*12 = 11988 (mensuel sur 12 mois)
        // Économie: 9999 - 11988 = -1989 (surcoût)
        assertThat(resp.path("status").asText()).isEqualTo("NO_SAVINGS");
    }

    /* ---------- 16. Horizon 12 mois ---------- */

    @Test
    @DisplayName("16. Horizon 12 mois")
    void horizon12Months() throws Exception {
        User user = activeUser("save16@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt16", OfferSector.MOBILE_PLAN, "https://mobile-alt16.com");
        CommercialOffer offer = createOffer(provider, "Horizon 12", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt16.com/horizon12", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("horizonMonths").asInt()).isEqualTo(12);
        assertThat(resp.path("currentTotalCostMinor").asLong()).isEqualTo(23988L);
    }

    /* ---------- 17. Horizon 24 mois ---------- */

    @Test
    @DisplayName("17. Horizon 24 mois")
    void horizon24Months() throws Exception {
        User user = activeUser("save17@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt17", OfferSector.MOBILE_PLAN, "https://mobile-alt17.com");
        CommercialOffer offer = createOffer(provider, "Horizon 24", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(2), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt17.com/horizon24", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                24, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("horizonMonths").asInt()).isEqualTo(24);
        assertThat(resp.path("currentTotalCostMinor").asLong()).isEqualTo(47976L);
    }

    /* ---------- 18. Offre incompatible ---------- */

    @Test
    @DisplayName("18. Offre incompatible - critère obligatoire non respecté")
    void incompatibleOffer() throws Exception {
        User user = activeUser("save18@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt18", OfferSector.MOBILE_PLAN, "https://mobile-alt18.com");
        CommercialOffer offer = createOffer(provider, "4G Seulement", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt18.com/4g", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "requires5G", true), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("INCOMPATIBLE_OFFER");
    }

    /* ---------- 19. Offre nécessitant une information ---------- */

    @Test
    @DisplayName("19. Offre nécessitant une information - NEEDS_INFORMATION")
    void needsInformationOffer() throws Exception {
        User user = activeUser("save19@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt19", OfferSector.MOBILE_PLAN, "https://mobile-alt19.com");
        CommercialOffer offer = createOffer(provider, "Sans Data", OfferSector.MOBILE_PLAN,
                "{\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt19.com/sans-data", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    /* ---------- 20. Offre expirée ---------- */

    @Test
    @DisplayName("20. Offre expirée")
    void expiredOffer() throws Exception {
        User user = activeUser("save20@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt20", OfferSector.MOBILE_PLAN, "https://mobile-alt20.com");
        CommercialOffer offer = createOffer(provider, "Expiré", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt20.com/expire", EvidenceStatus.VERIFIED);

        // L'offre expirée devrait lever une exception 400
        mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                                12, Map.of("minDataGb", 100), null, null)))
                .andExpect(status().isBadRequest());
    }

    /* ---------- 21. Offre non vérifiée ---------- */

    @Test
    @DisplayName("21. Offre non vérifiée")
    void unverifiedOffer() throws Exception {
        User user = activeUser("save21@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt21", OfferSector.MOBILE_PLAN, "https://mobile-alt21.com");
        CommercialOffer offer = createOffer(provider, "Brouillon", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.DRAFT);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);

        mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                                12, Map.of("minDataGb", 100), null, null)))
                .andExpect(status().isBadRequest());
    }

    /* ---------- 22. Utilisateur inéligible ---------- */

    @Test
    @DisplayName("22. Utilisateur inéligible - restrictions d'éligibilité")
    void ineligibleUser() throws Exception {
        User user = activeUser("save22@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt22", OfferSector.MOBILE_PLAN, "https://mobile-alt22.com");
        CommercialOffer offer = createOffer(provider, "Reservé Nouveaux", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                "Réservé aux nouveaux clients uniquement", "Aucun engagement", "FR",
                LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt22.com/reserve", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100, "eligibilityRestrictions", "aucun"), null, null);
        // L'offre a des restrictions, l'utilisateur demande "aucun" => critère non satisfait (obligatoire)
        assertThat(resp.path("status").asText()).isEqualTo("INCOMPATIBLE_OFFER");
    }

    /* ---------- 23. Catalogue vide ---------- */

    @Test
    @DisplayName("23. Catalogue vide - offre inexistante")
    void emptyCatalog() throws Exception {
        User user = activeUser("save23@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), UUID.randomUUID(), OfferSector.MOBILE_PLAN,
                                12, Map.of("minDataGb", 100), null, null)))
                .andExpect(status().isNotFound());
    }

    /* ---------- 24. Devise différente ---------- */

    @Test
    @DisplayName("24. Devise différente - rejet")
    void differentCurrency() throws Exception {
        User user = activeUser("save24@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt24", OfferSector.MOBILE_PLAN, "https://mobile-alt24.com");
        CommercialOffer offer = createOffer(provider, "USD Offer", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        OfferPricing pricing = createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        pricing.setCurrency("USD");
        pricingRepository.saveAndFlush(pricing);
        createEvidence(offer, "https://mobile-alt24.com/usd", EvidenceStatus.VERIFIED);

        mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                                12, Map.of("minDataGb", 100), null, null)))
                .andExpect(status().isBadRequest());
    }

    /* ---------- 25. Abonnement résilié ---------- */

    @Test
    @DisplayName("25. Abonnement résilié - INSUFFICIENT_DATA")
    void canceledSubscription() throws Exception {
        User user = activeUser("save25@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.CANCELED);

        OfferProvider provider = createProvider("MobileAlt25", OfferSector.MOBILE_PLAN, "https://mobile-alt25.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt25.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    /* ---------- 26. Abonnement en pause ---------- */

    @Test
    @DisplayName("26. Abonnement en pause - INSUFFICIENT_DATA")
    void pausedSubscription() throws Exception {
        User user = activeUser("save26@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.PAUSED);

        OfferProvider provider = createProvider("MobileAlt26", OfferSector.MOBILE_PLAN, "https://mobile-alt26.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt26.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("status").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    /* ---------- 27. Abonnement d'un autre utilisateur ---------- */

    @Test
    @DisplayName("27. Abonnement d'un autre utilisateur - 404")
    void otherUsersSubscription() throws Exception {
        User u1 = activeUser("save27a@eparly.test");
        User u2 = activeUser("save27b@eparly.test");
        Account account = createAccount(u1, "Compte");
        Subscription sub = createSubscription(u1, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt27", OfferSector.MOBILE_PLAN, "https://mobile-alt27.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt27.com/moins-cher", EvidenceStatus.VERIFIED);

        mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(u2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evalBody(sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                                12, Map.of("minDataGb", 100), null, null)))
                .andExpect(status().isNotFound());
    }

    /* ---------- 28. JWT absent ou invalide ---------- */

    @Test
    @DisplayName("28. JWT absent ou invalide")
    void missingOrInvalidJwt() throws Exception {
        mockMvc.perform(post("/v1/savings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/savings/methodology"))
                .andExpect(status().isUnauthorized());
    }

    /* ---------- 29. Aucun changement d'abonnement après estimation ---------- */

    @Test
    @DisplayName("29. Aucun changement d'abonnement après estimation")
    void noSubscriptionModification() throws Exception {
        User user = activeUser("save29@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        long initialDeclaredAmount = sub.getDeclaredAmountMinor();

        OfferProvider provider = createProvider("MobileAlt29", OfferSector.MOBILE_PLAN, "https://mobile-alt29.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt29.com/moins-cher", EvidenceStatus.VERIFIED);

        doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);

        // Vérifier que l'abonnement n'a pas été modifié
        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getDeclaredAmountMinor()).isEqualTo(initialDeclaredAmount);
        assertThat(updated.getDeclaredStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    /* ---------- 30. Explication du calcul cohérente ---------- */

    @Test
    @DisplayName("30. Explication du calcul cohérente")
    void coherentExplanation() throws Exception {
        User user = activeUser("save30@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt30", OfferSector.MOBILE_PLAN, "https://mobile-alt30.com");
        CommercialOffer offer = createOffer(provider, "Moins Cher", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt30.com/moins-cher", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("explanation").asText()).isNotBlank();
        assertThat(resp.path("assumptions").isArray()).isTrue();
        assertThat(resp.path("calculationSteps").isArray()).isTrue();
        assertThat(resp.path("calculationSteps").size()).isGreaterThan(0);
    }

    /* ---------- 31. Source officielle incluse ---------- */

    @Test
    @DisplayName("31. Source officielle incluse")
    void officialSourceIncluded() throws Exception {
        User user = activeUser("save31@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1999L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt31", OfferSector.MOBILE_PLAN, "https://mobile-alt31.com");
        CommercialOffer offer = createOffer(provider, "Avec Source", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":200,\"network5G\":true,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt31.com/source", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("officialUrl").asText()).isEqualTo("https://mobile-alt31.com/source");
        assertThat(resp.path("offerVerifiedAt").isMissingNode()).isFalse();
    }

    /* ---------- 32. Méthodologie endpoint ---------- */

    @Test
    @DisplayName("32. Endpoint de méthodologie")
    void methodologyEndpoint() throws Exception {
        User user = activeUser("save32@eparly.test");

        mockMvc.perform(get("/v1/savings/methodology")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formula").exists())
                .andExpect(jsonPath("$.supportedCurrency").value("EUR"))
                .andExpect(jsonPath("$.supportedHorizonsMonths").isArray());
    }

    /* ---------- 33. Économie nulle exactement ---------- */

    @Test
    @DisplayName("33. Économie exactement nulle")
    void exactlyZeroSavings() throws Exception {
        User user = activeUser("save33@eparly.test");
        Account account = createAccount(user, "Compte");
        Subscription sub = createSubscription(user, account, "Mon Mobile", "MobileActuel", 1000L,
                RecurringFrequency.MONTHLY, SubscriptionStatus.ACTIVE);

        OfferProvider provider = createProvider("MobileAlt33", OfferSector.MOBILE_PLAN, "https://mobile-alt33.com");
        CommercialOffer offer = createOffer(provider, "Meme Prix", OfferSector.MOBILE_PLAN,
                "{\"dataGb\":100,\"network5G\":false,\"commitment\":\"Aucun engagement\"}",
                null, "Aucun engagement", "FR", LocalDate.now(), LocalDate.now().plusYears(1), OfferStatus.VERIFIED);
        createPricing(offer, 1000L, OfferBillingPeriod.MONTHLY, null, null, null, null);
        createEvidence(offer, "https://mobile-alt33.com/meme", EvidenceStatus.VERIFIED);

        JsonNode resp = doEvaluate(user, sub.getId(), offer.getId(), OfferSector.MOBILE_PLAN,
                12, Map.of("minDataGb", 100), null, null);
        assertThat(resp.path("estimatedNetSavingsMinor").asLong()).isEqualTo(0);
        assertThat(resp.path("status").asText()).isEqualTo("NO_SAVINGS");
    }
}
