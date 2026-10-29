package com.eparly.api.auth.service;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.auth.config.RateLimitProperties;
import com.eparly.api.auth.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class RateLimiterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static RateLimitProperties createProperties(int loginMaxAttempts) {
        return new RateLimitProperties(
                true,
                false,
                List.of(),
                loginMaxAttempts, Duration.ofMinutes(5),
                loginMaxAttempts, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5)
        );
    }

    @Test
    @DisplayName("Progressive delay executes strictly outside active database transactions")
    void delayExecutesOutsideDatabaseTransaction() {
        AtomicBoolean transactionActiveDuringDelay = new AtomicBoolean(false);

        RateLimitProperties customProps = createProperties(5);

        RateLimiter rateLimiter = new RateLimiter(jdbcTemplate, customProps, transactionManager) {
            @Override
            public void recordLoginFailure(String ip, String normalizedEmail) {
                super.recordLoginFailure(ip, normalizedEmail);
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    transactionActiveDuringDelay.set(true);
                }
            }
        };

        String ip = "192.168.1.100";
        String email = "delay-test-" + UUID.randomUUID() + "@eparly.test";

        // Record 3 failures to trigger the progressive delay path (delay at >= 3 failures)
        rateLimiter.recordLoginFailure(ip, email);
        rateLimiter.recordLoginFailure(ip, email);
        rateLimiter.recordLoginFailure(ip, email);

        assertThat(transactionActiveDuringDelay.get()).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        // Verify the database counter was persisted and committed
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM rate_limit_entries WHERE entry_key = ?",
                Integer.class,
                "login:failed:email:" + email
        );
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Pre-login check enforces threshold lockout and throws RateLimitExceededException")
    void preLoginEnforcesThresholdLockout() {
        RateLimitProperties customProps = createProperties(2);

        RateLimiter rateLimiter = new RateLimiter(jdbcTemplate, customProps, transactionManager);
        String ip = "10.0.0.1";
        String email = "lockout-" + UUID.randomUUID() + "@eparly.test";

        // Initial check should pass
        rateLimiter.checkPreLogin(ip, email);

        // Record 2 failures (threshold = 2)
        rateLimiter.recordLoginFailure(ip, email);
        rateLimiter.recordLoginFailure(ip, email);

        // Third check must be blocked
        assertThatThrownBy(() -> rateLimiter.checkPreLogin(ip, email))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many requests");

        // Successful login resets counter for account
        rateLimiter.recordLoginSuccess(email);

        // Should not be blocked by account anymore (using distinct IP)
        rateLimiter.checkPreLogin("10.0.0.2", email);
    }

    @Test
    @DisplayName("Concurrent login failures update counters reliably without connection starvation")
    void concurrentFailuresDoNotExhaustConnections() throws InterruptedException {
        RateLimitProperties customProps = createProperties(50);

        RateLimiter rateLimiter = new RateLimiter(jdbcTemplate, customProps, transactionManager);
        String email = "concurrent-" + UUID.randomUUID() + "@eparly.test";

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    rateLimiter.recordLoginFailure("172.16.0." + index, email);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);

        Integer totalAttempts = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM rate_limit_entries WHERE entry_key = ?",
                Integer.class,
                "login:failed:email:" + email
        );
        assertThat(totalAttempts).isEqualTo(threadCount);
    }
}
