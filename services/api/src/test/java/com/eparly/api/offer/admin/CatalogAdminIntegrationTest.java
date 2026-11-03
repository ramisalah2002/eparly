package com.eparly.api.offer.admin;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.user.repository.UserRepository;
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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.eparly.api.user.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class CatalogAdminIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void permissionsArePersistentAndRevocableAndClientClaimsCannotGrantAdmin() throws Exception {
        User standard = createActiveUser();
        String forgedRoleToken = bearer(standard, true);

        mockMvc.perform(get("/v1/admin/catalog/providers"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/admin/catalog/providers/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/admin/catalog-sync/sources"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/catalog/providers/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/catalog-sync/sources")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/catalog-sync/candidates")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Attempted admin","sector":"STREAMING",
                                 "officialWebsite":"https://untrusted.example","country":"FR"}
                                """))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catalog_admin_permissions WHERE user_id = ?",
                Integer.class, standard.getId())).isZero();

        jdbcTemplate.update("""
                INSERT INTO catalog_admin_permissions(user_id, granted_by_user_id)
                VALUES (?, ?)
                """, standard.getId(), standard.getId());
        mockMvc.perform(get("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isOk());
        JsonNode pendingSource = response(mockMvc.perform(post("/v1/admin/catalog-sync/sources")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"Authorized test source","officialDomain":"127.0.0.1",
                                 "sourceUrl":"http://127.0.0.1:8081/offers.json",
                                 "sourceType":"JSON_FEED","sector":"STREAMING","country":"FR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionStatus").value("PENDING"))
                .andExpect(jsonPath("$.collectionEnabled").value(false))
                .andReturn());
        mockMvc.perform(patch("/v1/admin/catalog-sync/sources/{id}/permission",
                                pendingSource.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionStatus":"AUTHORIZED","collectionEnabled":true,
                                 "reason":"Official terms reviewed and collection authorized"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$.collectionEnabled").value(true));

        jdbcTemplate.update("""
                UPDATE catalog_admin_permissions SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, standard.getId());
        mockMvc.perform(get("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanCreateVerifyPublishReviseAndArchiveAnOffer() throws Exception {
        User administrator = createActiveUser();
        grantAdministrator(administrator.getId());
        String token = bearer(administrator, false);

        JsonNode provider = response(mockMvc.perform(post("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin test provider","sector":"STREAMING",
                                 "officialWebsite":"https://admin-catalog.example.test","country":"FR"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        String providerId = provider.get("id").asText();

        mockMvc.perform(get("/v1/admin/catalog/providers/{id}", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(providerId))
                .andExpect(jsonPath("$.name").value("Admin test provider"));
        mockMvc.perform(get("/v1/admin/catalog/providers/{id}/audit", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("PROVIDER_CREATED"));

        JsonNode draft = response(mockMvc.perform(post("/v1/admin/catalog/offers")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":"%s","commercialName":"Test plan",
                                 "sector":"STREAMING","characteristics":"{\\"screens\\":2}",
                                 "commitment":"Sans engagement","territory":"FR",
                                 "validFrom":"%s","validUntil":"%s"}
                                """.formatted(providerId, java.time.LocalDate.now(),
                                java.time.LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
                .andReturn());
        String offerId = draft.get("id").asText();
        mockMvc.perform(get("/v1/offers/{id}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());

        JsonNode price = response(mockMvc.perform(put("/v1/admin/catalog/offers/{id}/pricing", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountMinor":1999,"currency":"EUR","billingPeriod":"MONTHLY",
                                 "activationFeeMinor":0,"promotionPriceType":"PERCENTAGE",
                                 "promotionPercent":10,"promotionDurationMonths":2,
                                 "promotionValidFrom":"%s","promotionValidUntil":"%s",
                                 "taxesIncluded":true,"dataUnknown":false}
                                """.formatted(java.time.LocalDate.now(),
                                java.time.LocalDate.now().plusMonths(1))))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode evidence = response(mockMvc.perform(post("/v1/admin/catalog/offers/{id}/evidences", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"officialUrl":"https://admin-catalog.example.test/plans/test",
                                 "evidenceScope":"OFFER","nature":"Tarif et conditions",
                                 "provenance":"Consultation humaine","consultedAt":"%s"}
                                """.formatted(Instant.now().minusSeconds(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.verifiedAt").doesNotExist())
                .andReturn());
        String evidenceId = evidence.get("id").asText();

        JsonNode promotionProof = response(mockMvc.perform(post(
                                "/v1/admin/catalog/offers/{id}/evidences", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"officialUrl":"https://admin-catalog.example.test/offers/promo",
                                 "evidenceScope":"PROMOTION","nature":"Promotion",
                                 "provenance":"Consultation humaine","consultedAt":"%s"}
                                """.formatted(Instant.now().minusSeconds(1))))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode verified = response(mockMvc.perform(patch(
                                "/v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification",
                                offerId, evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"status":"VERIFIED",
                                 "summary":"Page officielle consultée et tarif confirmé"}
                                """.formatted(evidence.get("version").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedByUserId").value(administrator.getId().toString()))
                .andReturn());
        assertThat(verified.get("verifiedPricingVersion").asLong()).isEqualTo(price.get("version").asLong());
        mockMvc.perform(patch(
                                "/v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification",
                                offerId, promotionProof.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"status":"VERIFIED",
                                 "summary":"Promotion confirmée sur la page officielle"}
                                """.formatted(promotionProof.get("version").asText())))
                .andExpect(status().isOk());

        makePublishable(offerId);
        mockMvc.perform(post("/v1/admin/catalog/offers/{id}/publish", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("VERIFIED"));
        mockMvc.perform(get("/v1/offers/{id}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        JsonNode alternative = createAndPublishAlternative(token, providerId);
        JsonNode account = response(mockMvc.perform(post("/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin workflow test account"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        JsonNode subscription = response(mockMvc.perform(post("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","name":"Current streaming","provider":"Current provider",
                                 "declaredAmountMinor":3000,"currency":"EUR",
                                 "declaredFrequency":"MONTHLY","declaredStatus":"ACTIVE"}
                                """.formatted(account.get("id").asText())))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode comparison = response(mockMvc.perform(post("/v1/comparisons/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionId":"%s","sector":"STREAMING","requirements":{"minScreens":2}}
                                """.formatted(subscription.get("id").asText())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(comparison.path("compatibleOffers").toString())
                .contains("Test plan", "Cheaper alternative");

        MvcResult estimateResult = mockMvc.perform(post("/v1/savings/estimate")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionId":"%s","offerId":"%s","sector":"STREAMING",
                                 "horizonMonths":12,"userKnownCancellationFeeMinor":0,
                                 "userKnownOtherFeesMinor":0,"requirements":{"minScreens":2}}
                                """.formatted(subscription.get("id").asText(), alternative.get("id").asText())))
                .andReturn();
        assertThat(estimateResult.getResponse().getStatus())
                .withFailMessage(estimateResult.getResponse().getContentAsString())
                .isEqualTo(200);
        JsonNode estimate = response(estimateResult);
        assertThat(estimate.get("status").asText()).isEqualTo("SAVINGS_POSSIBLE");

        JsonNode revised = response(mockMvc.perform(put("/v1/admin/catalog/offers/{id}/pricing", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"amountMinor":2199,"currency":"EUR",
                                 "billingPeriod":"MONTHLY","activationFeeMinor":0,
                                 "taxesIncluded":true,"dataUnknown":false,"reason":"fixture revision"}
                                """.formatted(price.get("version").asText())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(revised.get("version").asLong()).isGreaterThan(price.get("version").asLong());
        mockMvc.perform(put("/v1/admin/catalog/offers/{id}/pricing", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"amountMinor":2099,"currency":"EUR",
                                 "billingPeriod":"MONTHLY","promotionPriceType":"FIXED_PRICE",
                                 "promotionPercent":10,"promotionDurationMonths":3,
                                 "priceAfterPromotionMinor":1000,"taxesIncluded":true,"dataUnknown":false}
                                """.formatted(price.get("version").asText())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/offers/{id}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        makePublishable(offerId);
        mockMvc.perform(post("/v1/admin/catalog/offers/{id}/publish", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification",
                                offerId, evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"status":"VERIFIED",
                                 "summary":"Tarif révisé contrôlé sur la page officielle"}
                                """.formatted(verified.get("version").asText())))
                .andExpect(status().isOk());
        makePublishable(offerId);
        mockMvc.perform(post("/v1/admin/catalog/offers/{id}/publish", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        JsonNode history = objectMapper.readTree(mockMvc.perform(get(
                                "/v1/admin/catalog/offers/{id}/price-history", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("oldPricingSnapshot").get("amountMinor").asLong()).isEqualTo(1999L);
        assertThat(history.get(0).get("newPricingSnapshot").get("amountMinor").asLong()).isEqualTo(2199L);
        mockMvc.perform(put("/v1/admin/catalog/offers/{id}/pricing", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"amountMinor":2000,"currency":"EUR",
                                 "billingPeriod":"MONTHLY","activationFeeMinor":0,
                                 "taxesIncluded":true,"dataUnknown":false}
                                """.formatted(price.get("version").asText())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/v1/admin/catalog/offers/{id}/archive", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("ARCHIVED"));
        mockMvc.perform(get("/v1/offers/{id}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());

        JsonNode audit = objectMapper.readTree(mockMvc.perform(get(
                                "/v1/admin/catalog/offers/{id}/audit", offerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(audit).isNotEmpty();
        assertThat(audit.get(0).get("actorUserId").asText()).isEqualTo(administrator.getId().toString());

        mockMvc.perform(patch("/v1/admin/catalog/providers/{id}", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/v1/offers/{id}", alternative.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerDetailsUpdatesValidationConflictsAndAuditUseTheAdminApi() throws Exception {
        User administrator = createActiveUser();
        grantAdministrator(administrator.getId());
        String token = bearer(administrator, false);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String name = "Provider CRUD " + unique;

        JsonNode created = response(mockMvc.perform(post("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sector":"STREAMING",
                                 "officialWebsite":"https://provider-%s.example","country":"FR"}
                                """.formatted(name, unique)))
                .andExpect(status().isCreated())
                .andReturn());
        String providerId = created.get("id").asText();

        mockMvc.perform(get("/v1/admin/catalog/providers/{id}", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.version").value(0));

        JsonNode updated = response(mockMvc.perform(patch("/v1/admin/catalog/providers/{id}", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"name":"%s updated",
                                 "officialWebsite":"https://updated-%s.example","country":"BE"}
                                """.formatted(name, unique)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name + " updated"))
                .andExpect(jsonPath("$.country").value("BE"))
                .andReturn());

        mockMvc.perform(patch("/v1/admin/catalog/providers/{id}", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"name":"Stale update"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/v1/admin/catalog/providers/{id}", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"officialWebsite":"http://not-secure.example"}
                                """.formatted(updated.get("version").asText())))
                .andExpect(status().isBadRequest());

        JsonNode duplicate = response(mockMvc.perform(post("/v1/admin/catalog/providers")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s duplicate","sector":"STREAMING",
                                 "officialWebsite":"https://duplicate-%s.example","country":"FR"}
                                """.formatted(name, unique)))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(patch("/v1/admin/catalog/providers/{id}", duplicate.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"name":"%s updated"}
                                """.formatted(name)))
                .andExpect(status().isConflict());

        JsonNode providerAudit = response(mockMvc.perform(get(
                                "/v1/admin/catalog/providers/{id}/audit", providerId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(providerAudit.toString()).contains("PROVIDER_CREATED", "PROVIDER_UPDATED");
    }

    private JsonNode createAndPublishAlternative(String token, String providerId) throws Exception {
        JsonNode draft = response(mockMvc.perform(post("/v1/admin/catalog/offers")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":"%s","commercialName":"Cheaper alternative",
                                 "sector":"STREAMING","characteristics":"{\\"screens\\":2}",
                                 "commitment":"Sans engagement","territory":"FR",
                                 "validFrom":"%s","validUntil":"%s"}
                                """.formatted(providerId, java.time.LocalDate.now(),
                                java.time.LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andReturn());
        String id = draft.get("id").asText();
        mockMvc.perform(put("/v1/admin/catalog/offers/{id}/pricing", id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountMinor":999,"currency":"EUR","billingPeriod":"MONTHLY",
                                 "activationFeeMinor":0,"promotionPriceType":"FIXED_PRICE",
                                 "promotionDurationMonths":3,"priceAfterPromotionMinor":499,
                                 "promotionValidFrom":"%s","promotionValidUntil":"%s",
                                 "taxesIncluded":true,"dataUnknown":false}
                                """.formatted(java.time.LocalDate.now(),
                                java.time.LocalDate.now().plusMonths(1))))
                .andExpect(status().isOk());
        JsonNode proof = response(mockMvc.perform(post("/v1/admin/catalog/offers/{id}/evidences", id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"officialUrl":"https://admin-catalog.example.test/plans/alternative",
                                 "evidenceScope":"OFFER","nature":"Tarif et conditions",
                                 "provenance":"Consultation humaine","consultedAt":"%s"}
                                """.formatted(Instant.now().minusSeconds(1))))
                .andExpect(status().isCreated())
                .andReturn());
        JsonNode promotionProof = response(mockMvc.perform(post("/v1/admin/catalog/offers/{id}/evidences", id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"officialUrl":"https://admin-catalog.example.test/promotions/alternative",
                                 "evidenceScope":"PROMOTION","nature":"Promotion",
                                 "provenance":"Consultation humaine","consultedAt":"%s"}
                                """.formatted(Instant.now().minusSeconds(1))))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(patch("/v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification",
                                id, proof.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"status":"VERIFIED",
                                 "summary":"Alternative confirmée sur le site officiel"}
                                """.formatted(proof.get("version").asText())))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification",
                                id, promotionProof.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%s,"status":"VERIFIED",
                                 "summary":"Promotion alternative contrôlée"}
                                """.formatted(promotionProof.get("version").asText())))
                .andExpect(status().isOk());
        makePublishable(id);
        return response(mockMvc.perform(post("/v1/admin/catalog/offers/{id}/publish", id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Remplit les conditions de donnees et de droits, pour que chaque test exerce sa propre regle. */
    private void makePublishable(Object offerId) {
        UUID id = UUID.fromString(offerId.toString());
        jdbcTemplate.update("UPDATE commercial_offers SET reuse_rights_status = 'ESTABLISHED', commitment = COALESCE(commitment, 'Sans engagement') WHERE id = ?", id);
        jdbcTemplate.update("UPDATE offer_pricing SET activation_fee_minor = COALESCE(activation_fee_minor, 0) WHERE offer_id = ?", id);
    }

    private User createActiveUser() {
        User user = new User("admin-test-" + UUID.randomUUID() + "@eparly.test", "irrelevant-hash");
        user.setStatus(com.eparly.api.user.domain.UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private void grantAdministrator(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO catalog_admin_permissions(user_id, granted_by_user_id)
                VALUES (?, ?)
                """, userId, userId);
    }

    private String bearer(User user, boolean forgedRole) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("eparly-test")
                .subject(user.getId().toString())
                .audience(List.of("eparly-test-client"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600));
        if (forgedRole) claims.claim("roles", List.of("CATALOG_ADMIN"));
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims.build()
        )).getTokenValue();
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
