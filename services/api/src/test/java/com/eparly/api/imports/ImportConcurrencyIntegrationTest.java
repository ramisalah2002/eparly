package com.eparly.api.imports;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Imports simultanes sur un meme compte : le verrou advisory serialise les imports, ON CONFLICT DO NOTHING sert de filet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class ImportConcurrencyIntegrationTest {

    private static final int THREADS = 6;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Plusieurs imports simultanes du meme fichier : N transactions au total, 'insere' cumule = N")
    void concurrentImportsOfTheSameFileDoNotDuplicate() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement(rows(1, 200, true)));

        List<JsonNode> jobs = runConcurrently(user, account, THREADS, index -> csv);

        assertThat(jobs).hasSize(THREADS);
        assertThat(jobs).allSatisfy(job -> assertThat(job.get("status").asText()).isEqualTo("COMPLETED"));
        assertThat(jobs.stream().mapToInt(job -> job.get("insertedRows").asInt()).sum()).isEqualTo(200);
        assertThat(jobs.stream().mapToInt(job -> job.get("skippedRows").asInt()).sum()).isEqualTo(200 * (THREADS - 1));
        assertThat(count(account)).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT fingerprint) FROM transactions WHERE account_id = ?", Long.class, account.getId()))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Imports simultanes de releves qui se chevauchent : aucun doublon, total = union des operations")
    void concurrentOverlappingStatementsDoNotDuplicate() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] first = utf8(statement(rows(1, 150, false)));
        byte[] second = utf8(statement(rows(100, 250, false)));

        List<JsonNode> jobs = runConcurrently(user, account, THREADS, index -> index % 2 == 0 ? first : second);

        assertThat(jobs).allSatisfy(job -> assertThat(job.get("status").asText()).isEqualTo("COMPLETED"));
        assertThat(jobs.stream().mapToInt(job -> job.get("insertedRows").asInt()).sum()).isEqualTo(250);
        assertThat(count(account)).isEqualTo(250);
    }

    @Test
    @DisplayName("Imports simultanes avec doublons legitimes identiques : le rang d'occurrence reste coherent")
    void concurrentImportsWithIdenticalRowsStayConsistent() throws Exception {
        User user = activeUser();
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        byte[] csv = utf8(statement(
                "04/03/2025;;CAFE;-2,50", "04/03/2025;;CAFE;-2,50", "04/03/2025;;CAFE;-2,50", "05/03/2025;;PAIN;-1,10"));

        List<JsonNode> jobs = runConcurrently(user, account, THREADS, index -> csv);

        assertThat(jobs.stream().mapToInt(job -> job.get("insertedRows").asInt()).sum()).isEqualTo(4);
        assertThat(count(account)).isEqualTo(4);
    }

    private List<JsonNode> runConcurrently(User user, Account account, int threads,
                                           java.util.function.IntFunction<byte[]> fileForThread) throws Exception {
        ImportHttpClient http = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                byte[] file = fileForThread.apply(i);
                Callable<JsonNode> task = () -> {
                    ready.countDown();
                    go.await();
                    return http.importOk(user, account.getId(), file, MAPPING_FR);
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Lignes distinctes numerotees de {@code from} a {@code to} inclus (libelle et montant uniques par ligne). */
    private static String[] rows(int from, int to, boolean withValueDate) {
        List<String> lines = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            int day = 1 + (i % 28);
            lines.add(String.format("%02d/03/2025;%s;OPERATION %d;-%d,%02d", day, withValueDate ? "%02d/03/2025".formatted(day) : "", i, i, i % 100));
        }
        return lines.toArray(String[]::new);
    }

    private long count(Account account) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?", Long.class, account.getId());
    }

    private User activeUser() {
        User user = new User("conc-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
