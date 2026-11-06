package com.eparly.api.insights;

import com.eparly.api.TestJwtConfiguration;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures entierement synthetiques, sur un PostgreSQL jetable. Le mois analyse est 2026-06, passe et complet. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class InsightsIntegrationTest {
    private static final UUID RESTAURANTS = UUID.fromString("550e8400-e001-0000-0000-000000000003");
    private static final UUID SHOPPING = UUID.fromString("550e8400-e001-0000-0000-000000000006");
    private static final UUID SUBSCRIPTIONS = UUID.fromString("550e8400-e001-0000-0000-000000000005");
    private static final UUID LEISURE = UUID.fromString("550e8400-e001-0000-0000-000000000008");
    private static final UUID TRANSFERS = UUID.fromString("550e8400-e001-0000-0000-00000000000b");
    private static final String MONTH = "2026-06";

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
        account = jdbc.queryForObject("INSERT INTO accounts (user_id, name, source_type) VALUES (?, 'Compte test', 'CSV') RETURNING id",
                UUID.class, user);
        job = jdbc.queryForObject("INSERT INTO import_jobs (user_id, account_id, file_hash, status) VALUES (?, ?, ?, 'COMPLETED') RETURNING id",
                UUID.class, user, account, hex());
    }

    private User newUser() {
        User created = new User("insights-" + UUID.randomUUID() + "@example.test", "irrelevant-hash");
        created.setStatus(UserStatus.ACTIVE);
        created.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(created);
    }

    private String bearer(User target) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("eparly-test").subject(target.getId().toString())
                .audience(List.of("eparly-test-client")).issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims)).getTokenValue();
    }

    private static String hex() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private UUID tx(String date, long amount, String label, UUID category, String type) {
        return jdbc.queryForObject("""
                INSERT INTO transactions (account_id, import_job_id, fingerprint, booking_date, amount_minor, currency,
                                          raw_label, normalized_label, category_id, transaction_type)
                VALUES (?, ?, ?, ?, ?, 'EUR', ?, ?, ?, ?) RETURNING id
                """, UUID.class, account, job, hex(), LocalDate.parse(date), amount, label, label, category, type);
    }

    /** Cinq mois complets : une operation en debut et en fin de mois, pour que chaque mois soit observe en entier. */
    private void completeMonths(long monthlyAmount, UUID category) {
        for (String month : List.of("2026-01", "2026-02", "2026-03", "2026-04", "2026-05")) {
            tx(month + "-02", -monthlyAmount / 2, "ACHAT " + category, category, "EXPENSE");
            tx(month + "-27", -monthlyAmount / 2, "ACHAT " + category, category, "EXPENSE");
        }
    }

    private void juneSpend(long first, long second, UUID category) {
        tx("2026-06-02", -first, "ACHAT JUIN", category, "EXPENSE");
        tx("2026-06-28", -second, "ACHAT JUIN", category, "EXPENSE");
    }

    private JsonNode getJson(String path) throws Exception {
        return getJson(path, token);
    }

    private JsonNode getJson(String path, String bearer) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode overview() throws Exception {
        return getJson("/v1/insights/overview?month=" + MONTH);
    }

    private JsonNode recommendations() throws Exception {
        return getJson("/v1/insights/recommendations?month=" + MONTH);
    }

    private List<String> recommendationTypes() throws Exception {
        List<String> types = new ArrayList<>();
        recommendations().path("recommendations").path("content").forEach(item -> types.add(item.path("type").asText()));
        return types;
    }

    private JsonNode firstOfType(String type) throws Exception {
        for (JsonNode item : recommendations().path("recommendations").path("content")) {
            if (type.equals(item.path("type").asText())) return item;
        }
        throw new AssertionError(type + " absent");
    }

    private JsonNode anomalies() throws Exception {
        return getJson("/v1/insights/anomalies?month=" + MONTH).path("anomalies").path("content");
    }

    private List<String> anomalyTypes() throws Exception {
        List<String> types = new ArrayList<>();
        anomalies().forEach(item -> types.add(item.path("type").asText()));
        return types;
    }

    private int setStatus(String id, String value, String bearer) throws Exception {
        return mvc.perform(patch("/v1/insights/recommendations/" + id + "?month=" + MONTH)
                        .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + value + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private void budget(UUID category, long limit) throws Exception {
        mvc.perform(put("/v1/budgets/" + category).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyLimitMinor\":" + limit + "}"))
                .andExpect(status().isOk());
    }

    private UUID series(String label) {
        return jdbc.queryForObject("""
                INSERT INTO recurring_series (account_id, normalized_label, currency, frequency, status, series_type,
                    usual_amount_minor, min_amount_minor, max_amount_minor, monthly_equivalent_minor, occurrence_count,
                    first_occurrence_date, last_occurrence_date)
                VALUES (?, ?, 'EUR', 'MONTHLY', 'CONFIRMED', 'SUBSCRIPTION', 1000, 1000, 1300, 1000, 6, '2026-01-02', '2026-06-02')
                RETURNING id
                """, UUID.class, account, label);
    }

    private void link(UUID seriesId, UUID transaction) {
        jdbc.update("INSERT INTO recurring_series_transactions (series_id, transaction_id) VALUES (?, ?)", seriesId, transaction);
    }

    @Test
    void userWithoutTransactionsGetsAnExplicitEmptyState() throws Exception {
        JsonNode overview = overview();
        assertThat(overview.path("expensesMinor").asLong()).isZero();
        assertThat(overview.path("changePercent").isNull()).isTrue();
        JsonNode quality = overview.path("dataQuality");
        assertThat(quality.path("sufficiency").asText()).isEqualTo("NO_DATA");
        assertThat(quality.path("analysisMethod").asText()).isEqualTo("DETERMINISTIC_STATISTICS");
        assertThat(quality.path("basedOnIncompleteData").asBoolean()).isTrue();
        assertThat(recommendationTypes()).isEmpty();
        assertThat(anomalyTypes()).isEmpty();
    }

    @Test
    void limitedHistoryProducesNoComparison() throws Exception {
        tx("2026-05-02", -5000, "RESTO A", RESTAURANTS, "EXPENSE");
        tx("2026-05-28", -5000, "RESTO A", RESTAURANTS, "EXPENSE");
        tx("2026-06-02", -40000, "RESTO A", RESTAURANTS, "EXPENSE");
        JsonNode overview = overview();
        assertThat(overview.path("dataQuality").path("sufficiency").asText()).isEqualTo("LIMITED_HISTORY");
        assertThat(overview.path("averageMonthlyExpensesMinor").isNull()).isTrue();
        assertThat(anomalyTypes()).isEmpty();
        assertThat(recommendationTypes()).doesNotContain("REDUCE_SPENDING");
    }

    @Test
    void realCategoryIncreaseIsDetectedWithExplanationAndGoal() throws Exception {
        completeMonths(10000, RESTAURANTS);
        UUID current = tx("2026-06-02", -14500, "RESTO X", RESTAURANTS, "EXPENSE");
        tx("2026-06-28", -500, "RESTO X", RESTAURANTS, "EXPENSE");

        JsonNode overview = overview();
        assertThat(overview.path("averageMonthlyExpensesMinor").asLong()).isEqualTo(10000);
        assertThat(overview.path("changePercent").asInt()).isEqualTo(50);
        assertThat(anomalyTypes()).contains("CATEGORY_SPIKE");

        JsonNode reduce = firstOfType("REDUCE_SPENDING");
        assertThat(reduce.path("description").asText()).contains("Restaurants").contains("50 €");
        assertThat(reduce.path("potentialAnnualSavingsMinor").asLong()).isEqualTo(3000 * 12);
        assertThat(reduce.path("evidenceIds").toString()).contains(current.toString());
        assertThat(reduce.path("status").asText()).isEqualTo("NEW");
    }

    @Test
    void smallVariationIsNotReported() throws Exception {
        completeMonths(10000, RESTAURANTS);
        juneSpend(5400, 5400, RESTAURANTS);
        assertThat(anomalyTypes()).doesNotContain("CATEGORY_SPIKE", "UNUSUAL_AMOUNT");
        assertThat(recommendationTypes()).doesNotContain("REDUCE_SPENDING");
    }

    @Test
    void risingCategoryIsATrendOnlyOverEnoughCompleteMonths() throws Exception {
        for (String[] month : new String[][]{{"2026-04", "4000"}, {"2026-05", "6000"}, {"2026-06", "9000"}}) {
            tx(month[0] + "-02", -Long.parseLong(month[1]), "RESTO", RESTAURANTS, "EXPENSE");
            tx(month[0] + "-28", -100, "RESTO", RESTAURANTS, "EXPENSE");
        }
        JsonNode trends = getJson("/v1/insights/spending-trends?month=" + MONTH);
        assertThat(trends.path("categories").get(0).path("trend").asText()).isEqualTo("RISING");
        assertThat(trends.path("categories").get(0).path("nonEssential").asBoolean()).isTrue();
    }

    @Test
    void unusualExpenseRequiresEnoughObservationsAndIsNeverCalledFraud() throws Exception {
        completeMonths(6000, SHOPPING);
        UUID large = tx("2026-06-15", -60000, "ACHAT EXCEPTIONNEL", SHOPPING, "EXPENSE");
        JsonNode unusual = null;
        for (JsonNode item : anomalies()) {
            if ("UNUSUAL_AMOUNT".equals(item.path("type").asText())) unusual = item;
        }
        assertThat(unusual).isNotNull();
        assertThat(unusual.path("transactionIds").toString()).contains(large.toString());
        assertThat(unusual.path("history").path("observations").asInt()).isEqualTo(10);
        assertThat(unusual.path("severity").asText()).isEqualTo("HIGH");
        assertThat(anomalies().toString().toLowerCase()).doesNotContain("fraud");
    }

    @Test
    void refundsAreNotExpensesAndTransfersAndUnknownsAreExcluded() throws Exception {
        tx("2026-06-02", -10000, "ACHAT", SHOPPING, "EXPENSE");
        tx("2026-06-10", 2500, "REMBOURSEMENT", SHOPPING, "REFUND");
        tx("2026-06-12", -50000, "VIREMENT EPARGNE", null, "TRANSFER");
        tx("2026-06-13", -70000, "VIREMENT LIVRET", TRANSFERS, "EXPENSE");
        tx("2026-06-14", 90000, "CREDIT INCONNU", null, "UNKNOWN");
        tx("2026-06-28", -3000, "NON QUALIFIE", null, "UNKNOWN");
        JsonNode overview = overview();
        assertThat(overview.path("expensesMinor").asLong()).isEqualTo(10000);
        assertThat(overview.path("refundsMinor").asLong()).isEqualTo(2500);
        assertThat(overview.path("confirmedIncomeMinor").asLong()).isZero();
        assertThat(overview.path("periodBalanceMinor").isNull()).isTrue();
        JsonNode quality = overview.path("dataQuality");
        assertThat(quality.path("excludedTransfers").asLong()).isEqualTo(2);
        assertThat(quality.path("unknownTransactions").asLong()).isEqualTo(2);
        assertThat(quality.path("unknownCreditsMinor").asLong()).isEqualTo(90000);
        assertThat(quality.path("unknownDebitsMinor").asLong()).isEqualTo(3000);
    }

    @Test
    void balanceIsOnlyGivenWithConfirmedIncomeOnACompleteMonth() throws Exception {
        juneSpend(5000, 5000, SHOPPING);
        tx("2026-06-03", 200000, "SALAIRE", null, "INCOME");
        assertThat(overview().path("periodBalanceMinor").asLong()).isEqualTo(190000);
    }

    @Test
    void otherCurrenciesAreNeverAddedAndEmptyMonthsAreNotZeros() throws Exception {
        completeMonths(10000, RESTAURANTS);
        jdbc.update("DELETE FROM transactions WHERE account_id = ? AND booking_date BETWEEN '2026-03-01' AND '2026-03-31'", account);
        juneSpend(5000, 5000, RESTAURANTS);
        tx("2026-06-05", -99900, "ACHAT USD", SHOPPING, "EXPENSE");
        jdbc.update("UPDATE transactions SET currency = 'USD' WHERE account_id = ? AND normalized_label = 'ACHAT USD'", account);

        JsonNode overview = overview();
        assertThat(overview.path("expensesMinor").asLong()).isEqualTo(10000);
        assertThat(overview.path("dataQuality").path("excludedCurrencies").path("USD").asLong()).isEqualTo(1);
        assertThat(overview.path("averageMonthlyExpensesMinor").asLong()).isEqualTo(10000);
        assertThat(overview.path("averageBasedOnMonths").asInt()).isEqualTo(4);

        boolean noData = false;
        for (JsonNode point : getJson("/v1/insights/spending-trends?month=" + MONTH).path("months")) {
            noData |= "2026-03".equals(point.path("month").asText()) && "NO_DATA".equals(point.path("coverage").asText());
        }
        assertThat(noData).isTrue();
    }

    @Test
    void currentMonthIsPartialAndFutureMonthIsRefused() throws Exception {
        String month = YearMonth.now(ZoneId.of("Europe/Paris")).toString();
        tx(month + "-01", -1000, "ACHAT", SHOPPING, "EXPENSE");
        JsonNode overview = getJson("/v1/insights/overview");
        assertThat(overview.path("month").asText()).isEqualTo(month);
        assertThat(overview.path("dataQuality").path("monthCoverage").asText()).isEqualTo("PARTIAL");
        mvc.perform(get("/v1/insights/overview?month=2999-01").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void possibleDuplicateIsFlaggedButRemainsAQuestion() throws Exception {
        tx("2026-06-10", -4500, "BOUTIQUE", SHOPPING, "EXPENSE");
        tx("2026-06-10", -4500, "BOUTIQUE", SHOPPING, "EXPENSE");
        tx("2026-06-10", -200, "CAFE", SHOPPING, "EXPENSE");
        tx("2026-06-10", -200, "CAFE", SHOPPING, "EXPENSE");
        JsonNode content = anomalies();
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).path("type").asText()).isEqualTo("POSSIBLE_DUPLICATE");
        assertThat(content.get(0).path("confidence").asText()).isEqualTo("MEDIUM");
        assertThat(content.get(0).path("explanation").asText()).contains("a verifier");
        assertThat(content.get(0).path("transactionIds").size()).isEqualTo(2);
    }

    @Test
    void repeatedBudgetOverrunIsReportedWithoutChangingTheBudget() throws Exception {
        completeMonths(10000, RESTAURANTS);
        juneSpend(5000, 5000, RESTAURANTS);
        budget(RESTAURANTS, 8000);
        assertThat(firstOfType("BUDGET_OVERRUN").path("confidence").asText()).isEqualTo("HIGH");
        assertThat(jdbc.queryForObject("SELECT monthly_limit_minor FROM category_budgets WHERE user_id = ?", Long.class, user))
                .isEqualTo(8000);
    }

    @Test
    void lowBudgetUsageIsOnlyASuggestion() throws Exception {
        completeMonths(10000, LEISURE);
        juneSpend(5000, 5000, LEISURE);
        budget(LEISURE, 50000);
        assertThat(firstOfType("BUDGET_LIMIT_HIGH").path("suggestedAction").asText()).contains("si vous le souhaitez");
    }

    @Test
    void recurringIncreaseUsesTheExistingSeriesAndIsNotAnUnusualPurchase() throws Exception {
        UUID series = series("STREAM A");
        for (String month : List.of("2026-01", "2026-02", "2026-03", "2026-04", "2026-05")) {
            link(series, tx(month + "-02", -1000, "STREAM A", SUBSCRIPTIONS, "EXPENSE"));
            tx(month + "-27", -20000, "LOYER", null, "EXPENSE");
        }
        link(series, tx("2026-06-02", -1300, "STREAM A", SUBSCRIPTIONS, "EXPENSE"));
        tx("2026-06-27", -20000, "LOYER", null, "EXPENSE");

        JsonNode increase = firstOfType("RECURRING_INCREASE");
        assertThat(increase.path("description").asText()).contains("10 €").contains("13 €");
        assertThat(increase.path("potentialAnnualSavingsMinor").asLong()).isEqualTo(3600);
        assertThat(anomalyTypes()).contains("RECURRING_AMOUNT_VARIATION").doesNotContain("UNUSUAL_AMOUNT");
        JsonNode fixedVariable = overview().path("fixedVariable");
        assertThat(fixedVariable.path("fixedMinor").asLong()).isEqualTo(1300);
        assertThat(fixedVariable.path("variableMinor").asLong()).isEqualTo(20000);
    }

    @Test
    void savingsPotentialIsAProjectionAndKeptApartFromPlannedAndObserved() throws Exception {
        completeMonths(20000, RESTAURANTS);
        juneSpend(10000, 10000, RESTAURANTS);
        JsonNode potential = firstOfType("SAVINGS_POTENTIAL");
        assertThat(potential.path("description").asText()).contains("20 €").contains("240 €");
        assertThat(potential.path("justification").asText()).contains("pas une économie réalisée");
        JsonNode savings = recommendations().path("savings");
        assertThat(savings.path("potentialEstimatedAnnualMinor").asLong()).isEqualTo(24000);
        assertThat(savings.path("observedMinor").isNull()).isTrue();
    }

    @Test
    void draftOffersAreNeverProposedAsAlternatives() throws Exception {
        UUID provider = jdbc.queryForObject("INSERT INTO offer_providers (name, sector, official_website) VALUES (?, 'MOBILE_PLAN', 'https://draft.example') RETURNING id",
                UUID.class, "Draft " + UUID.randomUUID());
        jdbc.update("INSERT INTO commercial_offers (provider_id, commercial_name, sector, characteristics, territory, publication_status) VALUES (?, 'Offre brouillon', 'MOBILE_PLAN', '{}', 'FR', 'DRAFT')",
                provider);
        JsonNode alternatives = recommendations().path("commercialAlternatives");
        assertThat(alternatives.path("status").asText()).isEqualTo("NONE_AVAILABLE");
        assertThat(alternatives.path("publishedOfferCount").asInt()).isZero();
        assertThat(recommendations().toString()).doesNotContain("Offre brouillon");
    }

    @Test
    void recommendationsAreRankedByStrengthAndImpact() throws Exception {
        completeMonths(10000, RESTAURANTS);
        completeMonths(6000, LEISURE);
        tx("2026-06-02", -40000, "RESTO", RESTAURANTS, "EXPENSE");
        juneSpend(3000, 3000, LEISURE);
        budget(RESTAURANTS, 5000);
        List<String> types = recommendationTypes();
        assertThat(types.get(0)).isEqualTo("BUDGET_OVERRUN");
        assertThat(types.indexOf("REDUCE_SPENDING")).isLessThan(types.indexOf("SAVINGS_POTENTIAL"));
        assertThat(types).doesNotHaveDuplicates();
        assertThat(recommendations().path("recommendations").path("content").get(0).path("priority").asText()).isEqualTo("HIGH");
    }

    @Test
    void ignoredRecommendationStaysHiddenThenReappearsWithTheSameId() throws Exception {
        completeMonths(10000, RESTAURANTS);
        juneSpend(14500, 500, RESTAURANTS);
        String id = firstOfType("REDUCE_SPENDING").path("id").asText();

        assertThat(setStatus(id, "VIEWED", token)).isEqualTo(204);
        assertThat(firstOfType("REDUCE_SPENDING").path("status").asText()).isEqualTo("VIEWED");
        assertThat(setStatus(id, "IGNORED", token)).isEqualTo(204);
        assertThat(setStatus(id, "IGNORED", token)).isEqualTo(204);
        assertThat(recommendationTypes()).doesNotContain("REDUCE_SPENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM insight_recommendation_states WHERE user_id = ?", Integer.class, user)).isEqualTo(1);

        jdbc.update("UPDATE insight_recommendation_states SET hidden_until = now() - interval '1 day' WHERE user_id = ?", user);
        JsonNode again = firstOfType("REDUCE_SPENDING");
        assertThat(again.path("id").asText()).isEqualTo(id);
        assertThat(again.path("status").asText()).isEqualTo("NEW");
    }

    @Test
    void usersAreIsolated() throws Exception {
        completeMonths(10000, RESTAURANTS);
        juneSpend(14500, 500, RESTAURANTS);
        String id = firstOfType("REDUCE_SPENDING").path("id").asText();
        String other = bearer(newUser());

        assertThat(getJson("/v1/insights/overview?month=" + MONTH, other).path("expensesMinor").asLong()).isZero();
        assertThat(getJson("/v1/insights/recommendations?month=" + MONTH, other).path("recommendations").path("totalElements").asLong()).isZero();
        assertThat(setStatus(id, "IGNORED", other)).isEqualTo(404);
        mvc.perform(get("/v1/insights/overview?accountId=" + account).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        assertThat(recommendationTypes()).contains("REDUCE_SPENDING");
        mvc.perform(get("/v1/insights/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void newTransactionsAndCategoryCorrectionAreTakenIntoAccountImmediately() throws Exception {
        completeMonths(10000, RESTAURANTS);
        UUID misclassified = tx("2026-06-02", -14500, "RESTO", SHOPPING, "EXPENSE");
        tx("2026-06-28", -500, "RESTO", RESTAURANTS, "EXPENSE");
        assertThat(recommendationTypes()).doesNotContain("REDUCE_SPENDING");

        mvc.perform(patch("/v1/transactions/" + misclassified + "/category").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":\"" + RESTAURANTS + "\"}"))
                .andExpect(status().isOk());
        assertThat(firstOfType("REDUCE_SPENDING").path("description").asText()).contains("50 €");

        // Meme effet qu'un nouvel import : aucun cache a invalider.
        tx("2026-06-20", -10000, "RESTO", RESTAURANTS, "EXPENSE");
        assertThat(firstOfType("REDUCE_SPENDING").path("description").asText()).contains("150 €");
    }

    @Test
    void concurrentStatusUpdatesLeaveASingleRow() throws Exception {
        completeMonths(10000, RESTAURANTS);
        juneSpend(14500, 500, RESTAURANTS);
        String id = firstOfType("REDUCE_SPENDING").path("id").asText();
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return setStatus(id, "DONE", token);
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<Integer> future : futures) assertThat(future.get()).isEqualTo(204);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM insight_recommendation_states WHERE user_id = ?", Integer.class, user)).isEqualTo(1);
    }
}
