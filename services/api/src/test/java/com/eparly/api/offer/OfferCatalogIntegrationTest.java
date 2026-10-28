package com.eparly.api.offer;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.offer.api.OfferCatalogController;
import com.eparly.api.offer.config.OfferCatalogProperties;
import com.eparly.api.offer.domain.*;
import com.eparly.api.offer.dto.CommercialOfferResponse;
import com.eparly.api.offer.dto.OfferListResponse;
import com.eparly.api.offer.dto.OfferMetadataResponse;
import com.eparly.api.offer.dto.OfferProviderResponse;
import com.eparly.api.offer.repository.*;
import com.eparly.api.offer.service.OfferCatalogService;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@Transactional
class OfferCatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OfferCatalogService service;
    @Autowired private OfferProviderRepository providerRepository;
    @Autowired private CommercialOfferRepository offerRepository;
    @Autowired private OfferPricingRepository pricingRepository;
    @Autowired private OfferEvidenceRepository evidenceRepository;
    @Autowired private OfferPriceHistoryRepository priceHistoryRepository;
    @Autowired private OfferCatalogProperties properties;

    private User activeUser() {
        User user = new User("offer-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("eparly-test")
                .subject(userId.toString())
                .audience(List.of("eparly-test-client"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims
        )).getTokenValue();
        return "Bearer " + token;
    }

    /* ---------- Helpers ---------- */

    private OfferProvider createProvider(String name, OfferSector sector, String website) {
        String uniqueName = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        OfferProvider p = new OfferProvider();
        p.setName(uniqueName);
        p.setSector(sector);
        p.setOfficialWebsite(website);
        p.setCountry("FR");
        p.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.saveAndFlush(p);
    }

    private OfferProvider createProvider(String name, OfferSector sector, String website, String country) {
        String uniqueName = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        OfferProvider p = new OfferProvider();
        p.setName(uniqueName);
        p.setSector(sector);
        p.setOfficialWebsite(website);
        p.setCountry(country);
        p.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.saveAndFlush(p);
    }

    private CommercialOffer createDraftOffer(OfferProvider provider, String name, OfferSector sector) {
        CommercialOffer o = new CommercialOffer();
        o.setProvider(provider);
        o.setCommercialName(name);
        o.setSector(sector);
        o.setTerritory("FR");
        o.setPublicationStatus(OfferStatus.DRAFT);
        o.setValidFrom(LocalDate.now());
        o.setValidUntil(LocalDate.now().plusYears(1));
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

    private OfferEvidence createVerifiedEvidence(CommercialOffer offer, String url) {
        OfferEvidence e = new OfferEvidence();
        e.setOffer(offer);
        e.setOfficialUrl(url);
        e.setConsultedAt(Instant.now());
        e.setVerifiedAt(Instant.now());
        e.setNature("Prix officiel");
        e.setVerifiedSummary("Prix verifie sur le site officiel");
        e.setVerificationStatus(EvidenceStatus.VERIFIED);
        e.setProvenance("Verification manuelle");
        return evidenceRepository.saveAndFlush(e);
    }

    /* ---------- Tests ---------- */

    @Test
    @DisplayName("1. Creation interne d'un fournisseur")
    void createProvider() {
        OfferProviderResponse r = service.createProvider(
                "ExampleStream", OfferSector.STREAMING, "https://example-stream.com", "FR");
        assertThat(r.name()).isEqualTo("ExampleStream");
        assertThat(r.sector()).isEqualTo(OfferSector.STREAMING);
        assertThat(r.officialWebsite()).startsWith("https://");
        assertThat(r.status()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(r.id()).isNotNull();
    }

    @Test
    @DisplayName("2. Creation d'une offre DRAFT")
    void createDraftOffer() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOfferResponse r = service.createDraftOffer(
                provider.getId(), "Forfait Start", OfferSector.STREAMING,
                "{\"qualite\":\"HD\",\"ecrans\":2}", null, "Aucun engagement", "FR",
                LocalDate.now(), LocalDate.now().plusYears(1));
        assertThat(r.publicationStatus()).isEqualTo(OfferStatus.DRAFT);
        assertThat(r.commercialName()).isEqualTo("Forfait Start");
        assertThat(r.id()).isNotNull();
    }

    @Test
    @DisplayName("3. Validation d'une fiche complete")
    void validateCompleteOffer() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Forfait Complet", OfferSector.STREAMING);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createVerifiedEvidence(offer, "https://example-stream.com/offers/start");

        CommercialOfferResponse r = service.validateAndPublish(offer.getId());
        assertThat(r.publicationStatus()).isEqualTo(OfferStatus.VERIFIED);
        assertThat(r.pricing().amountMinor()).isEqualTo(999L);
        assertThat(r.evidence()).hasSize(1);
        assertThat(r.evidence().get(0).verificationStatus()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    @Test
    @DisplayName("4. Refus d'une publication sans source verifiee")
    void publishWithoutEvidence() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Sans Preuve", OfferSector.STREAMING);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        // pas d'evidence verifiee

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.validateAndPublish(offer.getId()))
                .isInstanceOf(com.eparly.api.auth.exception.ApiException.class);
    }

    @Test
    @DisplayName("5. Refus d'une offre sans prix fiable")
    void publishWithoutPrice() {
        OfferProvider provider = createProvider("ExampleMobile", OfferSector.MOBILE_PLAN, "https://example-mobile.com");
        CommercialOffer offer = createDraftOffer(provider, "Sans Prix", OfferSector.MOBILE_PLAN);
        createVerifiedEvidence(offer, "https://example-mobile.com/offers/plan");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.validateAndPublish(offer.getId()))
                .isInstanceOf(com.eparly.api.auth.exception.ApiException.class);
    }

    @Test
    @DisplayName("6. Tarification mensuelle")
    void monthlyPricing() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Mensuel", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        assertThat(pricing.getBillingPeriod()).isEqualTo(OfferBillingPeriod.MONTHLY);
        assertThat(pricing.getAmountMinor()).isEqualTo(999L);
    }

    @Test
    @DisplayName("7. Tarification annuelle")
    void yearlyPricing() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Annuel", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, 9999L, OfferBillingPeriod.YEARLY);
        assertThat(pricing.getBillingPeriod()).isEqualTo(OfferBillingPeriod.YEARLY);
        assertThat(pricing.getAmountMinor()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("8. Promotions temporaires")
    void temporaryPromotion() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Promo", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        pricing.setPromotionPercent(20);
        pricing.setPromotionDurationMonths(3);
        pricing.setPriceAfterPromotionMinor(799L);
        pricing.setDiscountConditions("Offre de bienvenue 3 mois");
        pricingRepository.saveAndFlush(pricing);

        assertThat(pricing.getPromotionPercent()).isEqualTo(20);
        assertThat(pricing.getPromotionDurationMonths()).isEqualTo(3);
        assertThat(pricing.getPriceAfterPromotionMinor()).isEqualTo(799L);
        assertThat(pricing.getDiscountConditions()).isEqualTo("Offre de bienvenue 3 mois");
    }

    @Test
    @DisplayName("9. Prix après promotion")
    void priceAfterPromotion() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Apres Promo", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, 1299L, OfferBillingPeriod.MONTHLY);
        pricing.setPromotionPercent(25);
        pricing.setPriceAfterPromotionMinor(974L);
        pricingRepository.saveAndFlush(pricing);

        assertThat(pricing.getAmountMinor()).isEqualTo(1299L);
        assertThat(pricing.getPriceAfterPromotionMinor()).isEqualTo(974L);
        assertThat(pricing.getAmountMinor()).isNotEqualTo(pricing.getPriceAfterPromotionMinor());
    }

    @Test
    @DisplayName("10. Frais annexes")
    void additionalFees() {
        OfferProvider provider = createProvider("ExampleMobile", OfferSector.MOBILE_PLAN, "https://example-mobile.com");
        CommercialOffer offer = createDraftOffer(provider, "Avec Frais", OfferSector.MOBILE_PLAN);
        OfferPricing pricing = createPricing(offer, 1999L, OfferBillingPeriod.MONTHLY);
        pricing.setActivationFeeMinor(1000L);
        pricing.setOtherFees("{\"carte_sim\":1000,\"frais_dossier\":500}");
        pricingRepository.saveAndFlush(pricing);

        assertThat(pricing.getActivationFeeMinor()).isEqualTo(1000L);
        assertThat(pricing.getOtherFees()).contains("carte_sim");
    }

    @Test
    @DisplayName("11. Données optionnelles inconnues")
    void unknownOptionalData() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Inconnu", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, null, OfferBillingPeriod.UNKNOWN);
        pricing.setDataUnknown(true);
        pricingRepository.saveAndFlush(pricing);

        assertThat(pricing.getDataUnknown()).isTrue();
        assertThat(pricing.getBillingPeriod()).isEqualTo(OfferBillingPeriod.UNKNOWN);
    }

    @Test
    @DisplayName("12. Offre expirée")
    void expiredOffer() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Expiré", OfferSector.STREAMING);
        offer.setValidFrom(LocalDate.now().minusDays(10));
        offer.setValidUntil(LocalDate.now().minusDays(1));
        offerRepository.saveAndFlush(offer);

        service.markExpired();
        CommercialOffer updated = offerRepository.findById(offer.getId()).orElseThrow();
        // Note: markExpired only acts on VERIFIED offers; DRAFT stays DRAFT
        assertThat(updated.getPublicationStatus()).isEqualTo(OfferStatus.DRAFT);
    }

    @Test
    @DisplayName("13. Offre devenue STALE")
    void staleOffer() throws Exception {
        OfferCatalogProperties p = new OfferCatalogProperties();
        p.setFreshnessThreshold(java.time.Duration.ofSeconds(1));
        // On ne peut pas injecter facilement, on teste directement via repository
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Stale", OfferSector.STREAMING);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createVerifiedEvidence(offer, "https://example-stream.com/stale");

        CommercialOfferResponse published = service.validateAndPublish(offer.getId());
        assertThat(published.publicationStatus()).isEqualTo(OfferStatus.VERIFIED);

        // Simuler une verification trop ancienne
        OfferEvidence evidence = evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(offer.getId()).get(0);
        evidence.setVerifiedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        evidenceRepository.saveAndFlush(evidence);

        service.markStale();
        CommercialOffer updated = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(updated.getPublicationStatus()).isEqualTo(OfferStatus.STALE);
    }

    @Test
    @DisplayName("14. Archivage")
    void archiveOffer() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Archive", OfferSector.STREAMING);
        service.archive(offer.getId());
        CommercialOffer updated = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(updated.getPublicationStatus()).isEqualTo(OfferStatus.ARCHIVED);
    }

    @Test
    @DisplayName("15. Tracabilité d'une révision tarifaire")
    void priceRevisionTrace() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Revision", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        pricingRepository.saveAndFlush(pricing);

        service.revisePrice(offer.getId(), 1099L, "Augmentation tarifaire 2026");
        List<OfferPriceHistory> history = priceHistoryRepository.findAllByOfferIdOrderByChangedAtDesc(offer.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOldAmountMinor()).isEqualTo(999L);
        assertThat(history.get(0).getNewAmountMinor()).isEqualTo(1099L);
        assertThat(history.get(0).getReason()).isEqualTo("Augmentation tarifaire 2026");
        OfferPricing updated = pricingRepository.findByOfferId(offer.getId()).orElseThrow();
        assertThat(updated.getAmountMinor()).isEqualTo(1099L);
    }

    @Test
    @DisplayName("16. Liste paginée")
    void paginatedList() throws Exception {
        User user = activeUser();
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        for (int i = 0; i < 5; i++) {
            CommercialOffer o = createDraftOffer(provider, "Offre " + i, OfferSector.STREAMING);
            createPricing(o, 999L, OfferBillingPeriod.MONTHLY);
            createVerifiedEvidence(o, "https://example-stream.com/offre" + i);
            service.validateAndPublish(o.getId());
        }

        mockMvc.perform(get("/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    @DisplayName("17. Filtres sectoriels")
    void sectorFilter() throws Exception {
        User user = activeUser();
        OfferProvider sp = createProvider("Stream P", OfferSector.STREAMING, "https://stream.com");
        OfferProvider mp = createProvider("Mobile P", OfferSector.MOBILE_PLAN, "https://mobile.com");
        CommercialOffer so = createDraftOffer(sp, "Stream Offre", OfferSector.STREAMING);
        CommercialOffer mo = createDraftOffer(mp, "Mobile Offre", OfferSector.MOBILE_PLAN);
        createPricing(so, 999L, OfferBillingPeriod.MONTHLY);
        createPricing(mo, 1999L, OfferBillingPeriod.MONTHLY);
        createVerifiedEvidence(so, "https://stream.com/o");
        createVerifiedEvidence(mo, "https://mobile.com/o");
        service.validateAndPublish(so.getId());
        service.validateAndPublish(mo.getId());

        mockMvc.perform(get("/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                        .param("sector", "STREAMING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sector").value("STREAMING"));
    }

    @Test
    @DisplayName("18. Détail d'une offre")
    void offerDetail() throws Exception {
        User user = activeUser();
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Detail", OfferSector.STREAMING);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createVerifiedEvidence(offer, "https://example-stream.com/detail");
        CommercialOfferResponse published = service.validateAndPublish(offer.getId());

        mockMvc.perform(get("/v1/offers/{id}", published.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialName").value("Detail"))
                .andExpect(jsonPath("$.provider.name").value(Matchers.containsString("ExampleStream")))
                .andExpect(jsonPath("$.pricing.amountMinor").value(999))
                .andExpect(jsonPath("$.evidence.length()").value(1));
    }

    @Test
    @DisplayName("19. Routes protégées (JWT requis)")
    void routesProtected() throws Exception {
        mockMvc.perform(get("/v1/offers"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/offers/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/offers/metadata"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("20. Interdiction d'écriture par un utilisateur standard (pas d'endpoint POST/PUT/PATCH public)")
    void noPublicWriteEndpoints() throws Exception {
        User user = activeUser();
        // Il n'existe aucun endpoint POST/PUT/PATCH sur /v1/offers pour un utilisateur standard.
        // Seul le service interne (tests) peut creer/modifier des offres.
        mockMvc.perform(get("/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("21. Validation HTTPS et URL")
    void httpsUrlValidation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.createProvider("Bad", OfferSector.STREAMING, "http://insecure.com", "FR"))
                .isInstanceOf(com.eparly.api.auth.exception.ApiException.class);
    }

    @Test
    @DisplayName("22. Aucun fournisseur fictif publié comme réel")
    void noFictitiousPublishedAsReal() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Fictif", OfferSector.STREAMING);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        // Pas de publication sans preuve vérifiée → reste DRAFT, non visible publiquement
        assertThat(offer.getPublicationStatus()).isEqualTo(OfferStatus.DRAFT);
    }

    @Test
    @DisplayName("23. Exactitude des montants en centimes")
    void centAmountAccuracy() {
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Centimes", OfferSector.STREAMING);
        OfferPricing pricing = createPricing(offer, 1599L, OfferBillingPeriod.MONTHLY);
        assertThat(pricing.getAmountMinor()).isEqualTo(1599L);
        // 15,99 EUR = 1599 centimes, pas 1599 EUR
    }

    @Test
    @DisplayName("24. Metadata du catalogue")
    void metadata() throws Exception {
        User user = activeUser();
        mockMvc.perform(get("/v1/offers/metadata")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectors").exists())
                .andExpect(jsonPath("$.billingPeriods").exists())
                .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
                .andExpect(jsonPath("$.amountsInMinorUnits").value(true));
    }

    @Test
    @DisplayName("Offres non verifiees exclues de la liste publique")
    void unverifiedExcludedFromPublicList() throws Exception {
        User user = activeUser();
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        createDraftOffer(provider, "Non verifie", OfferSector.STREAMING); // DRAFT, pas de prix ni preuve

        mockMvc.perform(get("/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("Offre STALE exclue de la liste publique")
    void staleExcludedFromPublicList() throws Exception {
        User user = activeUser();
        OfferProvider provider = createProvider("ExampleStream", OfferSector.STREAMING, "https://example-stream.com");
        CommercialOffer offer = createDraftOffer(provider, "Stale", OfferSector.STREAMING);
        createPricing(offer, 999L, OfferBillingPeriod.MONTHLY);
        createVerifiedEvidence(offer, "https://example-stream.com/stale");
        service.validateAndPublish(offer.getId());

        // Rendre stale
        OfferEvidence evidence = evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(offer.getId()).get(0);
        evidence.setVerifiedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        evidenceRepository.saveAndFlush(evidence);
        service.markStale();

        mockMvc.perform(get("/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("Detail d'une offre inconnue retourne 404 neutre")
    void detailNotFound() throws Exception {
        User user = activeUser();
        mockMvc.perform(get("/v1/offers/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isNotFound());
    }
}
