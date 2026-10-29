package com.eparly.api.auth.service;

import com.eparly.api.auth.config.RateLimitProperties;
import com.eparly.api.auth.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiter.class);

    private final JdbcTemplate jdbcTemplate;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RateLimiter(JdbcTemplate jdbcTemplate, RateLimitProperties properties, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = Clock.systemUTC();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void checkRegister(String ip) {
        if (!properties.enabled()) return;
        checkAndIncrement(
                "register:ip:" + ip,
                properties.registerIpWindow(),
                properties.registerIpMaxAttempts(),
                "Registration attempt"
        );
    }

    public void checkResendVerification(String ip, String normalizedEmail) {
        if (!properties.enabled()) return;
        checkAndIncrement(
                "resend:ip:" + ip,
                properties.resendVerificationIpWindow(),
                properties.resendVerificationIpMaxAttempts(),
                "Resend verification (IP)"
        );
        checkAndIncrement(
                "resend:email:" + normalizedEmail,
                properties.resendVerificationEmailWindow(),
                properties.resendVerificationEmailMaxAttempts(),
                "Resend verification (Email)"
        );
    }

    public void checkForgotPassword(String ip, String normalizedEmail) {
        if (!properties.enabled()) return;
        checkAndIncrement(
                "forgot-password:ip:" + ip,
                properties.forgotPasswordIpWindow(),
                properties.forgotPasswordIpMaxAttempts(),
                "Forgot password (IP)"
        );
        checkAndIncrement(
                "forgot-password:email:" + normalizedEmail,
                properties.forgotPasswordEmailWindow(),
                properties.forgotPasswordEmailMaxAttempts(),
                "Forgot password (Email)"
        );
    }

    public void checkResetPassword(String ip) {
        if (!properties.enabled()) return;
        checkAndIncrement(
                "reset-password:ip:" + ip,
                properties.resetPasswordIpWindow(),
                properties.resetPasswordIpMaxAttempts(),
                "Reset password (IP)"
        );
    }

    public void checkRefresh(String ip) {
        if (!properties.enabled()) return;
        checkAndIncrement(
                "refresh:ip:" + ip,
                properties.refreshIpWindow(),
                properties.refreshIpMaxAttempts(),
                "Token refresh (IP)"
        );
    }

    public void checkPreLogin(String ip, String normalizedEmail) {
        if (!properties.enabled()) return;
        Instant now = clock.instant();

        checkIfBlocked("login:failed:ip:" + ip, properties.loginIpMaxAttempts(), now, "Login blocked for IP");
        checkIfBlocked("login:failed:email:" + normalizedEmail, properties.loginAccountMaxAttempts(), now, "Login blocked for account");
    }

    public void recordLoginFailure(String ip, String normalizedEmail) {
        if (!properties.enabled()) return;
        Instant now = clock.instant();

        // Counter increment runs in an isolated transaction that commits immediately
        Integer highestFailures = transactionTemplate.execute(status -> {
            int ipFailures = incrementCounter("login:failed:ip:" + ip, properties.loginIpWindow(), now);
            int emailFailures = incrementCounter("login:failed:email:" + normalizedEmail, properties.loginAccountWindow(), now);
            LOGGER.warn("Login failure recorded for IP and account (ipFailures={}, emailFailures={})", ipFailures, emailFailures);
            return Math.max(ipFailures, emailFailures);
        });

        int failures = highestFailures != null ? highestFailures : 0;

        // Progressive delay is executed strictly OUTSIDE database transaction boundaries
        // to prevent HikariCP connection pool starvation under repeated failed logins.
        if (failures >= 3 && failures < properties.loginAccountMaxAttempts()) {
            long delayMs = Math.min(2000L, (failures - 2) * 500L);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void recordLoginSuccess(String normalizedEmail) {
        if (!properties.enabled()) return;
        transactionTemplate.executeWithoutResult(status -> {
            try {
                jdbcTemplate.update("DELETE FROM rate_limit_entries WHERE entry_key = ?", "login:failed:email:" + normalizedEmail);
            } catch (DataAccessException exception) {
                LOGGER.warn("Could not reset login failure counter for account: {}", exception.getMessage());
            }
        });
    }

    private void checkIfBlocked(String key, int maxAttempts, Instant now, String logDescription) {
        try {
            var result = jdbcTemplate.query(
                    "SELECT attempt_count, expires_at FROM rate_limit_entries WHERE entry_key = ? AND expires_at > ?",
                    (rs, rowNum) -> new AttemptRecord(rs.getInt("attempt_count"), rs.getTimestamp("expires_at").toInstant()),
                    key,
                    Timestamp.from(now)
            );

            if (!result.isEmpty()) {
                AttemptRecord record = result.get(0);
                if (record.count() >= maxAttempts) {
                    long retryAfter = Math.max(1, Duration.between(now, record.expiresAt()).toSeconds());
                    LOGGER.warn("{} (attempts: {}/{}, retryAfter: {}s)", logDescription, record.count(), maxAttempts, retryAfter);
                    throw new RateLimitExceededException("Too many requests. Please try again later.", retryAfter);
                }
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (DataAccessException e) {
            LOGGER.error("Rate limit check failed in database: {}", e.getMessage());
        }
    }

    private int incrementCounter(String key, Duration window, Instant now) {
        Instant expiresAt = now.plus(window);
        try {
            String sql = """
                    INSERT INTO rate_limit_entries (entry_key, attempt_count, window_start, expires_at)
                    VALUES (?, 1, ?, ?)
                    ON CONFLICT (entry_key) DO UPDATE
                    SET attempt_count = CASE
                            WHEN rate_limit_entries.expires_at <= EXCLUDED.window_start THEN 1
                            ELSE rate_limit_entries.attempt_count + 1
                        END,
                        window_start = CASE
                            WHEN rate_limit_entries.expires_at <= EXCLUDED.window_start THEN EXCLUDED.window_start
                            ELSE rate_limit_entries.window_start
                        END,
                        expires_at = CASE
                            WHEN rate_limit_entries.expires_at <= EXCLUDED.window_start THEN EXCLUDED.expires_at
                            ELSE rate_limit_entries.expires_at
                        END
                    RETURNING attempt_count;
                    """;
            Integer count = jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    key,
                    Timestamp.from(now),
                    Timestamp.from(expiresAt)
            );
            return count != null ? count : 1;
        } catch (DataAccessException e) {
            LOGGER.error("Failed to increment rate limit counter for key {}: {}", key, e.getMessage());
            return 1;
        }
    }

    private void checkAndIncrement(String key, Duration window, int maxAttempts, String action) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(window);

        transactionTemplate.executeWithoutResult(status -> {
            try {
                String sql = """
                        INSERT INTO rate_limit_entries (entry_key, attempt_count, window_start, expires_at)
                        VALUES (?, 1, ?, ?)
                        ON CONFLICT (entry_key) DO UPDATE
                        SET attempt_count = CASE
                                WHEN rate_limit_entries.expires_at <= EXCLUDED.window_start THEN 1
                                ELSE rate_limit_entries.attempt_count + 1
                            END,
                            window_start = CASE
                                WHEN rate_limit_entries.expires_at <= EXCLUDED.window_start THEN EXCLUDED.window_start
                                ELSE rate_limit_entries.window_start
                            END,
                            expires_at = CASE
                                WHEN rate_limit_entries.expires_at <= EXCLUDED.window_start THEN EXCLUDED.expires_at
                                ELSE rate_limit_entries.expires_at
                            END
                        RETURNING attempt_count, expires_at;
                        """;

                var record = jdbcTemplate.queryForObject(
                        sql,
                        (rs, rowNum) -> new AttemptRecord(rs.getInt("attempt_count"), rs.getTimestamp("expires_at").toInstant()),
                        key,
                        Timestamp.from(now),
                        Timestamp.from(expiresAt)
                );

                if (record != null && record.count() > maxAttempts) {
                    long retryAfter = Math.max(1, Duration.between(now, record.expiresAt()).toSeconds());
                    LOGGER.warn("Rate limit exceeded for action: {} (attempts: {}/{}, retryAfter: {}s)", action, record.count(), maxAttempts, retryAfter);
                    throw new RateLimitExceededException("Too many requests. Please try again later.", retryAfter);
                }
            } catch (RateLimitExceededException e) {
                throw e;
            } catch (DataAccessException e) {
                LOGGER.error("Rate limit database operation failed for action {}: {}", action, e.getMessage());
            }
        });
    }

    public int cleanupExpired(Instant cutoff) {
        Integer deleted = transactionTemplate.execute(status -> {
            try {
                return jdbcTemplate.update("DELETE FROM rate_limit_entries WHERE expires_at < ?", Timestamp.from(cutoff));
            } catch (DataAccessException e) {
                LOGGER.error("Failed to cleanup expired rate limit entries: {}", e.getMessage());
                return 0;
            }
        });
        return deleted != null ? deleted : 0;
    }

    private record AttemptRecord(int count, Instant expiresAt) {
    }
}
