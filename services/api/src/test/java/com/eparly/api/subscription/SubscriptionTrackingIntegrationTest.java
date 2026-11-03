package com.eparly.api.subscription;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.recurring.service.LabelKey;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Donnees synthetiques sur un PostgreSQL jetable. Les dates sont relatives a aujourd'hui. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class SubscriptionTrackingIntegrationTest {
    private static final LocalDate TODAY = LocalDate.now();
    private static final UUID TRANSFERS = UUID.fromString("550e8400-e001-0000-0000-00000000000b");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;

    private UUID user;
    private UUID account;
    private UUID job;
    private String token;

    @BeforeEach
    void setUp() {
        User created = newUser();
        user = created.getId();
        token = bearer(created);
        account = newAccount(user, "Compte A");
        job = newJob(user, account);
    }

    private User newUser() {
        User created = new User("subs-" + UUID.randomUUID() + "@example.test", "irrelevant-hash");
        created.setStatus(UserStatus.ACTIVE);
        created.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(created);
    }

    private UUID newAccount(UUID owner, String name) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name, source_type) VALUES (?, ?, 'CSV') RETURNING id", UUID.class, owner, name);
    }

    private UUID newJob(UUID owner, UUID accountId) {
        return jdbc.queryForObject("INSERT INTO import_jobs (user_id, account_id, file_hash, status) VALUES (?, ?, ?, 'COMPLETED') RETURNING id",
                UUID.class, owner, accountId, hex());
    }

    private String bearer(User target) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("eparly-test").subject(target.getId().toString())
                .audience(List.of("eparly-test-client")).issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims)).getTokenValue();
    }

    private static String hex() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private UUID tx(LocalDate date, long amount, String label) {
        return txIn(account, job, date, amount, label, null, "EXPENSE");
    }

    private UUID txIn(UUID accountId, UUID jobId, LocalDate date, long amount, String label, UUID category, String type) {
        return jdbc.queryForObject("""
                INSERT INTO transactions (account_id, import_job_id, fingerprint, booking_date, amount_minor, currency, raw_label, normalized_label, category_id, transaction_type)
                VALUES (?, ?, ?, ?, ?, 'EUR', ?, ?, ?, ?) RETURNING id
                """, UUID.class, accountId, jobId, hex(), date, amount, label, label, category, type);
    }

    private void monthly(String label, long amount, int months) {
        for (int back = months; back >= 1; back--) tx(TODAY.minusMonths(back), -amount, label);
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchJson(String path, String body) throws Exception {
        return mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(String path) throws Exception {
        return read(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()));
    }

    private void analyze() throws Exception {
        postJson("/v1/recurring/analyze", "{}").andExpect(status().is2xxSuccessful());
    }

    private String createManual(String name, long amount, String frequency) throws Exception {
        return read(postJson("/v1/subscriptions", "{\"name\":\"" + name + "\",\"declaredAmountMinor\":" + amount + ",\"declaredFrequency\":\"" + frequency + "\",\"accountId\":\"" + account + "\"}")
                .andExpect(status().isCreated())).path("id").asText();
    }

    private String confirmDetected(int index) throws Exception {
        String seriesId = getJson("/v1/subscriptions/detected").get(index).path("seriesId").asText();
        return read(postJson("/v1/subscriptions", "{\"recurringSeriesId\":\"" + seriesId + "\"}").andExpect(status().isCreated())).path("id").asText();
    }

    private JsonNode requestAndConfirm(String id, LocalDate end) throws Exception {
        postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andExpect(status().isCreated());
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"REQUESTED\",\"requestedOn\":\"" + TODAY.minusMonths(6) + "\",\"channel\":\"EMAIL\",\"requestReference\":\"REF-1\"}")
                .andExpect(status().isOk());
        return read(postJson("/v1/subscriptions/" + id + "/cancellation/confirmation", "{\"expectedVersion\":1,\"source\":\"USER_DECLARED\",\"confirmedOn\":\"" + TODAY.minusMonths(6) + "\",\"providerEndDate\":\"" + end + "\"}")
                .andExpect(status().isOk()));
    }

    @Test
    void labelKeyAbsorbsReferencesButKeepsDistinctiveWords() {
        assertThat(LabelKey.of("PRLV SEPA NETFLIX.COM 0412")).isEqualTo("NETFLIX");
        assertThat(LabelKey.of("NETFLIX 998")).isEqualTo("NETFLIX");
        assertThat(LabelKey.of("NETFLIX PREMIUM")).isNotEqualTo(LabelKey.of("NETFLIX"));
    }

    @Test
    void monthlyDetectionWithLabelVariantsStaysAProposalAndIsNotDuplicated() throws Exception {
        for (int back = 6; back >= 1; back--) tx(TODAY.minusMonths(back), -1599, "PRLV NETFLIX " + (1000 + back));
        analyze();
        analyze();
        JsonNode detected = getJson("/v1/subscriptions/detected");
        assertThat(detected.size()).isEqualTo(1);
        assertThat(detected.get(0).path("frequency").asText()).isEqualTo("MONTHLY");
        assertThat(detected.get(0).path("occurrenceCount").asInt()).isEqualTo(6);
        assertThat(getJson("/v1/subscriptions").size()).isZero();

        String id = confirmDetected(0);
        JsonNode confirmed = getJson("/v1/subscriptions/" + id);
        assertThat(confirmed.path("origin").asText()).isEqualTo("AUTO_DETECTED");
        assertThat(confirmed.path("annualEquivalentMinor").decimalValue().longValue()).isEqualTo(1599 * 12);
        assertThat(getJson("/v1/subscriptions/detected").size()).isZero();
        analyze();
        assertThat(getJson("/v1/subscriptions").size()).isEqualTo(1);
        assertThat(getJson("/v1/subscriptions/" + id + "/transactions").path("transactions").size()).isEqualTo(6);
    }

    @Test
    void yearlyDetectionNeedsOnlyTwoPaymentsButKeepsAModestConfidence() throws Exception {
        tx(TODAY.minusYears(2).plusDays(3), -4900, "ASSOCIATION ANNUELLE");
        tx(TODAY.minusYears(1).plusDays(3), -4900, "ASSOCIATION ANNUELLE");
        tx(TODAY.minusMonths(2), -3000, "ACHAT ISOLE");
        tx(TODAY.minusMonths(1), -3000, "ACHAT ISOLE");
        analyze();
        JsonNode detected = getJson("/v1/subscriptions/detected");
        assertThat(detected.size()).isEqualTo(1);
        assertThat(detected.get(0).path("frequency").asText()).isEqualTo("YEARLY");
        assertThat(detected.get(0).path("confidenceScore").asInt()).isLessThanOrEqualTo(60);
    }

    @Test
    void variableAmountIsDetectedAndTransfersAreExcluded() throws Exception {
        long[] amounts = {4000, 6500, 5200, 7100, 4800};
        for (int index = 0; index < amounts.length; index++) tx(TODAY.minusMonths(index + 1), -amounts[index], "ENERGIE VERTE");
        for (int back = 5; back >= 1; back--) txIn(account, job, TODAY.minusMonths(back), -20000, "EPARGNE AUTOMATIQUE", TRANSFERS, "EXPENSE");
        analyze();
        JsonNode detected = getJson("/v1/subscriptions/detected");
        assertThat(detected.size()).isEqualTo(1);
        assertThat(detected.get(0).path("label").asText()).isEqualTo("ENERGIE VERTE");
    }

    @Test
    void twoProductsOfOneProviderAreNotMergedAndOtherAccountIsOnlyLinkedOnRequest() throws Exception {
        monthly("GOOGLE ONE", 299, 4);
        monthly("GOOGLE WORKSPACE", 800, 4);
        UUID second = newAccount(user, "Compte B");
        UUID secondJob = newJob(user, second);
        for (int back = 4; back >= 1; back--) txIn(second, secondJob, TODAY.minusMonths(back), -1000, "SPOTIFY", null, "EXPENSE");
        for (int back = 8; back >= 5; back--) tx(TODAY.minusMonths(back), -1000, "SPOTIFY");
        analyze();
        JsonNode detected = getJson("/v1/subscriptions/detected");
        assertThat(detected.size()).isEqualTo(4);

        List<String> spotify = new ArrayList<>();
        detected.forEach(item -> {
            if ("SPOTIFY".equals(item.path("label").asText())) spotify.add(item.path("seriesId").asText());
        });
        String id = read(postJson("/v1/subscriptions", "{\"recurringSeriesId\":\"" + spotify.get(0) + "\"}").andExpect(status().isCreated())).path("id").asText();
        JsonNode remaining = null;
        for (JsonNode item : getJson("/v1/subscriptions/detected")) {
            if ("SPOTIFY".equals(item.path("label").asText())) remaining = item;
        }
        assertThat(remaining.path("possibleExistingSubscriptionId").asText()).isEqualTo(id);
        postJson("/v1/subscriptions/" + id + "/linked-series", "{\"seriesId\":\"" + spotify.get(1) + "\"}").andExpect(status().isOk());
        postJson("/v1/subscriptions/" + id + "/linked-series", "{\"seriesId\":\"" + spotify.get(1) + "\"}").andExpect(status().isConflict());
        assertThat(getJson("/v1/subscriptions/" + id + "/transactions").path("transactions").size()).isEqualTo(8);
        postJson("/v1/subscriptions", "{\"recurringSeriesId\":\"" + spotify.get(1) + "\"}").andExpect(status().isConflict());
    }

    @Test
    void manualCorrectionsSurviveDetectionAndAreHistorized() throws Exception {
        monthly("SERVICE CLOUD", 500, 6);
        analyze();
        String id = confirmDetected(0);
        patchJson("/v1/subscriptions/" + id, "{\"name\":\"Mon cloud\",\"declaredAmountMinor\":450,\"commitmentEndDate\":\"" + TODAY.plusMonths(3) + "\"}").andExpect(status().isOk());
        tx(TODAY, -700, "SERVICE CLOUD");
        analyze();
        JsonNode after = getJson("/v1/subscriptions/" + id);
        assertThat(after.path("name").asText()).isEqualTo("Mon cloud");
        assertThat(after.path("declaredAmountMinor").asLong()).isEqualTo(450);
        assertThat(after.path("commitmentEndDate").asText()).isEqualTo(TODAY.plusMonths(3).toString());
        JsonNode history = getJson("/v1/subscriptions/" + id + "/history");
        assertThat(history.get(0).path("type").asText()).isEqualTo("FIELDS_CORRECTED");
        assertThat(history.get(0).path("details").asText()).contains("Mon cloud");

        patchJson("/v1/subscriptions/" + id, "{\"unknownFields\":[\"AMOUNT\",\"COMMITMENT_END_DATE\"]}").andExpect(status().isOk());
        JsonNode unknown = getJson("/v1/subscriptions/" + id);
        assertThat(unknown.path("declaredAmountMinor").isNull()).isTrue();
        assertThat(unknown.path("commitmentEndDate").isNull()).isTrue();
        patchJson("/v1/subscriptions/" + id, "{\"unknownFields\":[\"NAME\"]}").andExpect(status().isBadRequest());
    }

    @Test
    void archivedSubscriptionLeavesTheListAndOverview() throws Exception {
        String id = createManual("Magazine", 900, "MONTHLY");
        assertThat(getJson("/v1/subscriptions/overview").path("currencies").get(0).path("annualEstimatedMinor").asLong()).isEqualTo(10800);
        postJson("/v1/subscriptions/" + id + "/archive", "{}").andExpect(status().isOk());
        assertThat(getJson("/v1/subscriptions").size()).isZero();
        assertThat(getJson("/v1/subscriptions/overview").path("currencies").size()).isZero();
        assertThat(getJson("/v1/subscriptions/" + id).path("archivedAt").isNull()).isFalse();
    }

    @Test
    void overviewNormalizesFrequenciesKeepsCurrenciesApartAndCountsUnknowns() throws Exception {
        createManual("Hebdo", 100, "WEEKLY");
        createManual("Trimestre", 3000, "QUARTERLY");
        createManual("Annuel", 12000, "YEARLY");
        createManual("Sans frequence", 500, "UNKNOWN");
        postJson("/v1/subscriptions", "{\"name\":\"Etranger\",\"declaredAmountMinor\":1000,\"currency\":\"USD\",\"declaredFrequency\":\"MONTHLY\"}").andExpect(status().isCreated());
        JsonNode overview = getJson("/v1/subscriptions/overview");
        assertThat(overview.path("unknownCostCount").asInt()).isEqualTo(1);
        assertThat(overview.path("currencies").size()).isEqualTo(2);
        JsonNode eur = overview.path("currencies").get(0);
        assertThat(eur.path("currency").asText()).isEqualTo("EUR");
        assertThat(eur.path("annualEstimatedMinor").asLong()).isEqualTo(5200 + 12000 + 12000);
        assertThat(overview.path("currencies").get(1).path("annualEstimatedMinor").asLong()).isEqualTo(12000);
    }

    @Test
    void priceIncreaseGivesOldAndNewAmountsAndEvidence() throws Exception {
        monthly("VIDEO PLUS", 999, 5);
        UUID latest = tx(TODAY, -1299, "VIDEO PLUS");
        analyze();
        confirmDetected(0);
        JsonNode increase = getJson("/v1/subscriptions/analysis").path("priceIncreases").get(0);
        assertThat(increase.path("oldAmountMinor").asLong()).isEqualTo(999);
        assertThat(increase.path("newAmountMinor").asLong()).isEqualTo(1299);
        assertThat(increase.path("differenceMinor").asLong()).isEqualTo(300);
        assertThat(increase.path("transactionIds").toString()).contains(latest.toString());
    }

    @Test
    void redundancyAndReviewStayCautiousAboutUsage() throws Exception {
        createManual("Netflix", 1599, "MONTHLY");
        createManual("Disney+", 1199, "MONTHLY");
        String costly = createManual("Salle de sport", 4000, "MONTHLY");
        patchJson("/v1/subscriptions/" + costly, "{\"noLongerNeeded\":true}").andExpect(status().isOk());
        JsonNode analysis = getJson("/v1/subscriptions/analysis");
        assertThat(analysis.path("possibleRedundancies").get(0).path("family").asText()).isEqualTo("VIDEO");
        assertThat(analysis.path("possibleRedundancies").get(0).path("explanation").asText()).contains("rester tous utiles");
        assertThat(analysis.path("toReview").toString()).contains("USER_MARKED_NOT_NEEDED").contains("HIGH_COST");
        assertThat(analysis.toString().toLowerCase()).doesNotContain("inutilis").doesNotContain("doublon");
    }

    @Test
    void renewalsDistinguishConfirmedAndEstimatedDates() throws Exception {
        monthly("JOURNAL NUMERIQUE", 800, 4);
        analyze();
        String estimated = confirmDetected(0);
        String confirmed = createManual("Assurance mobile", 1000, "MONTHLY");
        patchJson("/v1/subscriptions/" + confirmed, "{\"nextDueDate\":\"" + TODAY.plusDays(10) + "\",\"commitmentEndDate\":\"" + TODAY.plusDays(20) + "\"}").andExpect(status().isOk());
        JsonNode renewals = getJson("/v1/subscriptions/renewals?days=60");
        String text = renewals.toString();
        assertThat(text).contains("COMMITMENT_END");
        for (JsonNode item : renewals) {
            if (item.path("subscriptionId").asText().equals(estimated)) assertThat(item.path("dateStatus").asText()).isEqualTo("ESTIMATED");
            if (item.path("subscriptionId").asText().equals(confirmed) && "RENEWAL".equals(item.path("kind").asText())) {
                assertThat(item.path("dateStatus").asText()).isEqualTo("CONFIRMED");
                assertThat(item.path("date").asText()).isEqualTo(TODAY.plusDays(10).toString());
            }
        }
    }

    @Test
    void cancellationWorkflowIsHistorizedAndRefusesInconsistentTransitions() throws Exception {
        String id = createManual("Presse", 1000, "MONTHLY");
        JsonNode planned = read(postJson("/v1/subscriptions/" + id + "/cancellation", "{\"notes\":\"Trop cher\"}").andExpect(status().isCreated()));
        assertThat(planned.path("status").asText()).isEqualTo("PLANNED");
        assertThat(planned.path("providerVerified").asBoolean()).isFalse();
        postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andExpect(status().isConflict());

        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"ENDED\"}").andExpect(status().isConflict());
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"REQUESTED\"}").andExpect(status().isBadRequest());
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"REQUESTED\",\"requestedOn\":\"" + TODAY.plusDays(1) + "\"}").andExpect(status().isBadRequest());
        JsonNode requested = read(patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"REQUESTED\",\"requestedOn\":\"" + TODAY + "\",\"channel\":\"ONLINE_ACCOUNT\",\"requestReference\":\"ABC\"}").andExpect(status().isOk()));
        assertThat(requested.path("verification").path("state").asText()).isEqualTo("AWAITING_END_DATE");
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"notes\":\"Perime\"}").andExpect(status().isConflict());
        assertThat(getJson("/v1/subscriptions/" + id).path("declaredStatus").asText()).isEqualTo("ACTIVE");

        JsonNode confirmed = read(postJson("/v1/subscriptions/" + id + "/cancellation/confirmation", "{\"expectedVersion\":1,\"source\":\"USER_HOLDS_PROVIDER_DOCUMENT\",\"confirmedOn\":\"" + TODAY + "\",\"reference\":\"DOC\"}").andExpect(status().isOk()));
        assertThat(confirmed.path("confirmationSource").asText()).isEqualTo("USER_HOLDS_PROVIDER_DOCUMENT");
        assertThat(confirmed.path("providerVerified").asBoolean()).isFalse();
        assertThat(getJson("/v1/subscriptions/" + id).path("declaredStatus").asText()).isEqualTo("ACTIVE");

        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":2,\"status\":\"ENDED\"}").andExpect(status().isOk());
        assertThat(getJson("/v1/subscriptions/" + id).path("declaredStatus").asText()).isEqualTo("CANCELED");
        List<String> changes = new ArrayList<>();
        getJson("/v1/subscriptions/" + id + "/history").forEach(item -> {
            if ("CANCELLATION_STATUS_CHANGED".equals(item.path("type").asText())) changes.add(item.path("details").asText());
        });
        assertThat(changes).hasSize(4);
    }

    @Test
    void failedCancellationCanBeRetriedAndKeepsTheContractActive() throws Exception {
        String id = createManual("Telephone", 2000, "MONTHLY");
        postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andExpect(status().isCreated());
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"REQUESTED\",\"requestedOn\":\"" + TODAY + "\"}").andExpect(status().isOk());
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":1,\"status\":\"FAILED\"}").andExpect(status().isOk());
        assertThat(getJson("/v1/subscriptions/" + id).path("declaredStatus").asText()).isEqualTo("ACTIVE");
        postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andExpect(status().isCreated());
    }

    @Test
    void chargeAfterEndDateCreatesASingleBusinessEventAndAnInsight() throws Exception {
        monthly("MUSIQUE PLUS", 1000, 8);
        analyze();
        String id = confirmDetected(0);
        requestAndConfirm(id, TODAY.minusMonths(3));
        JsonNode cancellation = getJson("/v1/subscriptions/" + id + "/cancellation");
        assertThat(cancellation.path("verification").path("state").asText()).isEqualTo("CHARGE_OBSERVED");
        assertThat(cancellation.path("verification").path("explanation").asText()).contains("A verifier");

        assertThat(getJson("/v1/subscriptions/post-cancellation-charges").size()).isGreaterThan(0);
        int first = getJson("/v1/subscriptions/post-cancellation-charges").size();
        assertThat(getJson("/v1/subscriptions/post-cancellation-charges").size()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_events WHERE subscription_id = ?::uuid AND event_type = 'POST_CANCELLATION_CHARGE' AND notified_at IS NULL", Integer.class, id)).isEqualTo(first);

        JsonNode recommendations = getJson("/v1/insights/recommendations").path("recommendations").path("content");
        assertThat(recommendations.get(0).path("type").asText()).isEqualTo("POST_CANCELLATION_CHARGE");
        assertThat(getJson("/v1/subscriptions/" + id + "/savings").path("observedMinor").isNull()).isTrue();
    }

    @Test
    void chargeWithinGraceDaysIsNotSuspect() throws Exception {
        String id = createManual("GRAND JOURNAL", 1000, "MONTHLY");
        tx(TODAY.minusMonths(2), -1000, "GRAND JOURNAL");
        tx(TODAY.minusMonths(1).plusDays(2), -1000, "GRAND JOURNAL");
        requestAndConfirm(id, TODAY.minusMonths(1));
        assertThat(getJson("/v1/subscriptions/" + id + "/cancellation").path("verification").path("state").asText()).isNotEqualTo("CHARGE_OBSERVED");
    }

    @Test
    void incompleteImportsNeverProveACancellationNorProduceRealSavings() throws Exception {
        for (int back = 12; back >= 7; back--) tx(TODAY.minusMonths(back), -1000, "CLUB MENSUEL");
        analyze();
        String id = confirmDetected(0);
        requestAndConfirm(id, TODAY.minusMonths(6).plusDays(1));
        JsonNode verification = getJson("/v1/subscriptions/" + id + "/cancellation").path("verification");
        assertThat(verification.path("state").asText()).isEqualTo("INSUFFICIENT_DATA");
        JsonNode savings = getJson("/v1/subscriptions/" + id + "/savings");
        assertThat(savings.path("afterConfirmationAnnualMinor").asLong()).isEqualTo(12000);
        assertThat(savings.path("observedMinor").isNull()).isTrue();
    }

    @Test
    void observedSavingsRequireCoveredCyclesWithoutCharge() throws Exception {
        for (int back = 12; back >= 7; back--) tx(TODAY.minusMonths(back), -1000, "CLUB MENSUEL");
        for (int back = 6; back >= 0; back--) tx(TODAY.minusMonths(back), -2500, "BOULANGERIE " + back);
        analyze();
        String id = null;
        for (JsonNode item : getJson("/v1/subscriptions/detected")) {
            if ("CLUB MENSUEL".equals(item.path("label").asText())) {
                id = read(postJson("/v1/subscriptions", "{\"recurringSeriesId\":\"" + item.path("seriesId").asText() + "\"}")).path("id").asText();
            }
        }
        requestAndConfirm(id, TODAY.minusMonths(6).plusDays(1));
        JsonNode verification = getJson("/v1/subscriptions/" + id + "/cancellation").path("verification");
        assertThat(verification.path("state").asText()).isEqualTo("NO_CHARGE_OBSERVED");
        assertThat(verification.path("explanation").asText()).contains("pas une preuve");
        JsonNode savings = getJson("/v1/subscriptions/" + id + "/savings");
        int cycles = savings.path("observedCycles").asInt();
        assertThat(cycles).isGreaterThanOrEqualTo(2);
        assertThat(savings.path("observedMinor").asLong()).isEqualTo(1000L * cycles);
    }

    @Test
    void savingsNeverCountARequestAsRealizedAndAlternativesStayEmpty() throws Exception {
        String id = createManual("Coffre numerique", 1000, "MONTHLY");
        JsonNode before = getJson("/v1/subscriptions/" + id + "/savings");
        assertThat(before.path("theoreticalAnnualMinor").asLong()).isEqualTo(12000);
        assertThat(before.path("plannedAnnualMinor").isNull()).isTrue();
        assertThat(before.path("commercialAlternatives").path("status").asText()).isEqualTo("NONE_AVAILABLE");

        postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andExpect(status().isCreated());
        patchJson("/v1/subscriptions/" + id + "/cancellation", "{\"expectedVersion\":0,\"status\":\"REQUESTED\",\"requestedOn\":\"" + TODAY + "\"}").andExpect(status().isOk());
        patchJson("/v1/subscriptions/" + id, "{\"declaredAmountMinor\":5000}").andExpect(status().isOk());
        JsonNode requested = getJson("/v1/subscriptions/" + id + "/savings");
        assertThat(requested.path("plannedAnnualMinor").asLong()).isEqualTo(12000);
        assertThat(requested.path("afterConfirmationAnnualMinor").isNull()).isTrue();
        assertThat(requested.path("observedMinor").isNull()).isTrue();
    }

    @Test
    void insightsGainSubscriptionSignalsWithoutBreakingAndWithoutDuplicates() throws Exception {
        String flagged = createManual("Salle premium", 7000, "MONTHLY");
        patchJson("/v1/subscriptions/" + flagged, "{\"noLongerNeeded\":true,\"nextDueDate\":\"" + TODAY.plusDays(5) + "\"}").andExpect(status().isOk());
        String stale = createManual("Vieux magazine", 500, "MONTHLY");
        postJson("/v1/subscriptions/" + stale + "/cancellation", "{}").andExpect(status().isCreated());
        jdbc.update("UPDATE subscription_cancellations SET decided_at = now() - interval '20 days' WHERE subscription_id = ?::uuid", stale);

        JsonNode response = getJson("/v1/insights/recommendations");
        List<String> types = new ArrayList<>();
        response.path("recommendations").path("content").forEach(item -> types.add(item.path("type").asText()));
        assertThat(types).contains("SUBSCRIPTION_SAVINGS", "SUBSCRIPTION_RENEWAL", "CANCELLATION_UNFINISHED").doesNotHaveDuplicates();
        assertThat(response.path("savings").path("potentialEstimatedAnnualMinor").asLong()).isEqualTo(84000);
        assertThat(response.path("commercialAlternatives").path("status").asText()).isEqualTo("NONE_AVAILABLE");
        for (String path : List.of("/v1/insights/overview", "/v1/insights/spending-trends", "/v1/insights/anomalies")) getJson(path);
    }

    @Test
    void usersAreIsolated() throws Exception {
        String id = createManual("Prive", 1000, "MONTHLY");
        postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andExpect(status().isCreated());
        String other = bearer(newUser());
        for (String path : List.of("", "/transactions", "/history", "/savings", "/cancellation")) {
            mvc.perform(get("/v1/subscriptions/" + id + path).header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/v1/subscriptions/" + id + "/archive").header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isNotFound());
        mvc.perform(post("/v1/subscriptions/" + id + "/cancellation").header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/subscriptions/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentStartsLeaveASingleOpenCancellation() throws Exception {
        String id = createManual("Concurrence", 1000, "MONTHLY");
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return postJson("/v1/subscriptions/" + id + "/cancellation", "{}").andReturn().getResponse().getStatus();
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get());
            assertThat(statuses.stream().filter(code -> code == 201)).hasSize(1);
            assertThat(statuses).containsOnly(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_cancellations WHERE subscription_id = ?::uuid", Integer.class, id)).isEqualTo(1);
    }
}
