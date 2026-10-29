package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.bearer;
import static com.eparly.api.imports.ImportTestSupport.createAccount;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class MonthlyReportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("HTTP: empty month is explicit and report requires a JWT and month")
    void emptyMonthAndRequiredParameters() throws Exception {
        User user = activeUser();
        String path = "/v1/reports/monthly";

        mockMvc.perform(get(path).param("month", "2025-01"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .param("month", "2025-13"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .param("month", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.month").value("2025-01"))
                .andExpect(jsonPath("$.period.startDate").value("2025-01-01"))
                .andExpect(jsonPath("$.period.endDate").value("2025-01-31"))
                .andExpect(jsonPath("$.financialSummary.totalExpensesMinor").value(0))
                .andExpect(jsonPath("$.financialSummary.confirmedIncomeMinor").value(0))
                .andExpect(jsonPath("$.financialSummary.transactionCount").value(0))
                .andExpect(jsonPath("$.categoryBreakdown.length()").value(0))
                .andExpect(jsonPath("$.previousMonthComparison.changePercentage").doesNotExist())
                .andExpect(jsonPath("$.dataQuality.noTransactionsRecorded").value(true))
                .andExpect(jsonPath("$.dataQuality.coverageStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.verificationSummary.observedSavingsMinor").doesNotExist());
    }

    @Test
    @DisplayName("PostgreSQL: monthly totals follow transaction types and include UNKNOWN negatives")
    void aggregatesFinancialTypesCategoriesAndPreviousMonth() throws Exception {
        User user = activeUser();
        Account account = importedAccount(user, "Report totals", "01/03/2025;01/03/2025;BASE;-1,00");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), -5_000, "EXPENSE", "extra-expense");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), -300, "FEE", "fee");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), 100_000, "INCOME", "income");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), -9_900, "TRANSFER", "transfer");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), 500, "REFUND", "refund");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), 600, "UNKNOWN", "unknown-positive");
        insertTransaction(user, account, LocalDate.of(2025, 3, 31), -250, "UNKNOWN", "unknown-negative");
        insertTransaction(user, account, LocalDate.of(2025, 2, 1), -1_000, "EXPENSE", "previous-expense");

        String response = mockMvc.perform(get("/v1/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialSummary.currency").value("EUR"))
                .andExpect(jsonPath("$.financialSummary.totalExpensesMinor").value(5_400))
                .andExpect(jsonPath("$.financialSummary.confirmedIncomeMinor").value(100_000))
                .andExpect(jsonPath("$.financialSummary.uncategorizedExpensesMinor").value(5_400))
                .andExpect(jsonPath("$.financialSummary.unclassifiedPositiveMinor").value(1_100))
                .andExpect(jsonPath("$.financialSummary.unclassifiedNegativeMinor").value(250))
                .andExpect(jsonPath("$.financialSummary.transactionCount").value(8))
                .andExpect(jsonPath("$.previousMonthComparison.previousExpensesMinor").value(1_000))
                .andExpect(jsonPath("$.previousMonthComparison.changeMinor").value(4_400))
                .andExpect(jsonPath("$.previousMonthComparison.changePercentage").value(440.00))
                .andExpect(jsonPath("$.dataQuality.transactionCountIncluded").value(8))
                .andExpect(jsonPath("$.dataQuality.accountCountIncluded").value(1))
                .andExpect(jsonPath("$.dataQuality.firstObservedBookingDate").value("2025-03-01"))
                .andExpect(jsonPath("$.dataQuality.lastObservedBookingDate").value("2025-03-31"))
                .andExpect(jsonPath("$.dataQuality.coverageStatus").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(response);
        assertThat(json.get("categoryBreakdown").size()).isEqualTo(1);
        assertThat(json.at("/categoryBreakdown/0/categoryName").asText()).isEqualTo("Non catégorisé");
        assertThat(json.at("/categoryBreakdown/0/amountMinor").asLong()).isEqualTo(5_400);
        assertThat(json.at("/categoryBreakdown/0/transactionCount").asLong()).isEqualTo(3);
        assertThat(json.at("/categoryBreakdown/0/percentage").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(json.at("/verificationSummary/observedSavingsMinor").isNull()).isTrue();
        assertThat(json.at("/dataQuality/basedOnCsvImports").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("PostgreSQL: account filter and user boundary apply to the entire report")
    void filtersAccountsAndIsolatesUsers() throws Exception {
        User owner = activeUser();
        User other = activeUser();
        Account first = importedAccount(owner, "Report account 1", "01/03/2025;01/03/2025;FIRST;-10,00");
        Account second = importedAccount(owner, "Report account 2", "31/03/2025;31/03/2025;SECOND;-20,00");
        Account foreign = importedAccount(other, "Foreign report account", "15/03/2025;15/03/2025;PRIVATE;-99,00");
        String authorization = bearer(jwtEncoder, owner.getId());

        mockMvc.perform(get("/v1/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialSummary.totalExpensesMinor").value(3_000))
                .andExpect(jsonPath("$.financialSummary.transactionCount").value(2))
                .andExpect(jsonPath("$.dataQuality.accountCountIncluded").value(2));

        mockMvc.perform(get("/v1/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("month", "2025-03")
                        .param("accountId", first.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialSummary.totalExpensesMinor").value(1_000))
                .andExpect(jsonPath("$.financialSummary.transactionCount").value(1))
                .andExpect(jsonPath("$.dataQuality.accountCountIncluded").value(1));

        mockMvc.perform(get("/v1/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("month", "2025-03")
                        .param("accountId", foreign.getId().toString()))
                .andExpect(status().isNotFound());

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    @DisplayName("PostgreSQL: non-EUR accounts are excluded without a false no-activity claim")
    void excludesIncompatibleAccountCurrencies() throws Exception {
        User user = activeUser();
        Account account = importedAccount(user, "USD report account", "01/03/2025;01/03/2025;USD TX;-10,00");
        jdbcTemplate.update("UPDATE accounts SET currency = 'USD' WHERE id = ?", account.getId());

        mockMvc.perform(get("/v1/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, user.getId()))
                        .param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialSummary.totalExpensesMinor").value(0))
                .andExpect(jsonPath("$.financialSummary.transactionCount").value(0))
                .andExpect(jsonPath("$.dataQuality.excludedCurrencyTransactionCount").value(1))
                .andExpect(jsonPath("$.dataQuality.incompatibleAccountCount").value(1))
                .andExpect(jsonPath("$.dataQuality.accountCountIncluded").value(0))
                .andExpect(jsonPath("$.dataQuality.noTransactionsRecorded").value(false));
    }

    @Test
    @DisplayName("PostgreSQL: subscriptions, alternatives, snapshots and partial evidence remain distinct")
    void reportsDeclaredSubscriptionsAndIndividualSavingsVerifications() throws Exception {
        User owner = activeUser();
        Account account = importedAccount(owner, "Savings report account", "01/03/2025;01/03/2025;SUBSCRIPTION;-1,00");
        UUID activeSubscription = insertSubscription(owner, account, "Active service", 1_500, "MONTHLY", "ACTIVE");
        insertSubscription(owner, account, "Paused service", 2_500, "MONTHLY", "PAUSED");
        insertSubscription(owner, account, "Canceled service", 3_500, "MONTHLY", "CANCELED");
        UUID firstOffer = insertOffer("Report provider 1", "Report offer 1");
        UUID secondOffer = insertOffer("Report provider 2", "Report offer 2");

        UUID plannedAction = insertAction(
                owner, activeSubscription, firstOffer, "PLANNED", "PARTIALLY_VERIFIED", 12, 6_000);
        UUID canceledAction = insertAction(
                owner, activeSubscription, secondOffer, "CANCELED", "NOT_VERIFIED", 24, 24_000);
        UUID oldPaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM transactions WHERE account_id = ? ORDER BY booking_date LIMIT 1",
                UUID.class,
                account.getId()
        );
        jdbcTemplate.update("""
                INSERT INTO savings_action_verifications (
                    id, action_id, user_id, status, evaluated_at,
                    old_monthly_amount_minor, new_monthly_amount_minor, observed_difference_minor,
                    covered_from, covered_to, missing_information, contradictions, decision_reasons
                )
                VALUES (?, ?, ?, 'PARTIALLY_VERIFIED', ?, 999, 699, 300,
                        '2025-02-01', '2025-03-31', CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb))
                """,
                UUID.randomUUID(),
                plannedAction,
                owner.getId(),
                Timestamp.from(Instant.parse("2025-04-01T00:00:00Z")),
                "[\"Paiement suivant manquant\"]",
                "[\"Écart de date\"]",
                "[\"Preuves bancaires partielles\"]"
        );
        jdbcTemplate.update("""
                INSERT INTO savings_action_evidences (
                    id, user_id, action_id, transaction_id, role, provenance, confirmed_at
                )
                VALUES (?, ?, ?, ?, 'OLD_SUBSCRIPTION_PAYMENT', 'USER_CONFIRMED', CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                owner.getId(),
                plannedAction,
                oldPaymentId
        );

        User other = activeUser();
        UUID foreignSubscription = insertSubscription(other, null, "Private service", 9_999, "MONTHLY", "ACTIVE");
        insertAction(other, foreignSubscription, firstOffer, "DECLARED", "NOT_VERIFIED", 12, 99_999);

        String body = mockMvc.perform(get("/v1/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtEncoder, owner.getId()))
                        .param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionsSummary.activeDeclaredCount").value(1))
                .andExpect(jsonPath("$.subscriptionsSummary.pausedDeclaredCount").value(1))
                .andExpect(jsonPath("$.subscriptionsSummary.canceledDeclaredCount").value(1))
                .andExpect(jsonPath("$.subscriptionsSummary.declaredMonthlyEquivalentMinor").value(1_500))
                .andExpect(jsonPath("$.subscriptionsSummary.declaredAnnualEquivalentMinor").value(18_000))
                .andExpect(jsonPath("$.savingsActionsSummary.plannedCount").value(1))
                .andExpect(jsonPath("$.savingsActionsSummary.canceledCount").value(1))
                .andExpect(jsonPath("$.savingsActionsSummary.opportunities.length()").value(2))
                .andExpect(jsonPath("$.verificationSummary.partiallyVerifiedCount").value(1))
                .andExpect(jsonPath("$.verificationSummary.notVerifiedCount").value(1))
                .andExpect(jsonPath("$.verificationSummary.observedSavingsMinor").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(body);
        var partialVerification = StreamSupport.stream(
                        json.at("/verificationSummary/actions").spliterator(), false)
                .filter(item -> item.get("status").asText().equals("PARTIALLY_VERIFIED"))
                .findFirst()
                .orElseThrow();
        assertThat(partialVerification.get("observedMonthlyDifferenceMinorGross").asLong()).isEqualTo(300);
        assertThat(partialVerification.at("/contradictions/0").asText()).isEqualTo("Écart de date");
        assertThat(partialVerification.get("observedPayments").size()).isEqualTo(1);
        var opportunities = json.at("/savingsActionsSummary/opportunities");
        assertThat(opportunities.size()).isEqualTo(2);
        assertThat(opportunities.get(0).get("horizonMonths").asInt())
                .isNotEqualTo(opportunities.get(1).get("horizonMonths").asInt());
        assertThat(StreamSupport.stream(opportunities.spliterator(), false)
                .map(item -> item.get("subscriptionName").asText())
                .toList())
                .containsOnly("Active service");
    }

    private Account importedAccount(User user, String name, String csvLine) throws Exception {
        Account account = createAccount(mockMvc, jwtEncoder, objectMapper, user, name);
        ImportHttpClient client = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper);
        client.importOk(user, account.getId(), utf8(statement(csvLine)), MAPPING_FR);
        return account;
    }

    private UUID insertSubscription(
            User user,
            Account account,
            String name,
            long amountMinor,
            String frequency,
            String status
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO subscriptions (
                    id, user_id, account_id, name, declared_amount_minor, currency,
                    declared_frequency, next_due_date, declared_status, origin
                )
                VALUES (?, ?, ?, ?, ?, 'EUR', ?, '2025-04-15', ?, 'USER_DECLARED')
                """,
                id,
                user.getId(),
                account != null ? account.getId() : null,
                name,
                amountMinor,
                frequency,
                status
        );
        return id;
    }

    private UUID insertOffer(String providerName, String offerName) {
        UUID providerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO offer_providers (id, name, sector, official_website)
                VALUES (?, ?, 'STREAMING', 'https://example.test')
                """, providerId, providerName);
        jdbcTemplate.update("""
                INSERT INTO commercial_offers (id, provider_id, commercial_name, sector, publication_status)
                VALUES (?, ?, ?, 'STREAMING', 'VERIFIED')
                """, offerId, providerId, offerName);
        return offerId;
    }

    private UUID insertAction(
            User user,
            UUID subscriptionId,
            UUID offerId,
            String status,
            String verificationStatus,
            int horizonMonths,
            long potentialSavingsMinor
    ) {
        UUID id = UUID.randomUUID();
        String snapshot = """
                {
                  "calculationVersion":"1.0.0",
                  "horizonMonths":%d,
                  "currency":"EUR",
                  "currentTotalCostMinor":30000,
                  "currentCostSource":"USER_DECLARED",
                  "alternativeTotalCostMinor":10000,
                  "switchingCostMinor":0,
                  "potentialNetSavingsMinor":%d,
                  "estimationStatus":"SAVINGS_POSSIBLE",
                  "offerVerifiedAt":"2026-10-01T00:00:00Z",
                  "estimatedAt":"2026-10-15T00:00:00Z",
                  "assumptions":[],
                  "missingInformation":[],
                  "officialUrl":"https://example.test"
                }
                """.formatted(horizonMonths, potentialSavingsMinor);
        jdbcTemplate.update("""
                INSERT INTO savings_actions (
                    id, user_id, subscription_id, alternative_offer_id, status,
                    verification_status, horizon_months, estimate_snapshot
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                id,
                user.getId(),
                subscriptionId,
                offerId,
                status,
                verificationStatus,
                horizonMonths,
                snapshot
        );
        return id;
    }

    private void insertTransaction(
            User user,
            Account account,
            LocalDate bookingDate,
            long amountMinor,
            String type,
            String fingerprint
    ) {
        UUID importJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM import_jobs WHERE account_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class,
                account.getId()
        );
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    id, account_id, import_job_id, fingerprint, booking_date,
                    amount_minor, currency, raw_label, normalized_label, transaction_type,
                    custom_category_flag, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'EUR', ?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                account.getId(),
                importJobId,
                fingerprint,
                bookingDate,
                amountMinor,
                fingerprint,
                fingerprint,
                type
        );
    }

    private User activeUser() {
        User user = new User("monthly-report-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
