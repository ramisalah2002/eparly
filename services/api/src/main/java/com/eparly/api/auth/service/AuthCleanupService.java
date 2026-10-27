package com.eparly.api.auth.service;

import com.eparly.api.auth.config.AuthCleanupProperties;
import com.eparly.api.auth.repository.EmailVerificationTokenRepository;
import com.eparly.api.auth.repository.PasswordResetTokenRepository;
import com.eparly.api.auth.repository.RefreshTokenSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuthCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthCleanupService.class);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final RateLimiter rateLimiter;
    private final AuthCleanupProperties properties;
    private final Clock clock;

    public AuthCleanupService(
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            RateLimiter rateLimiter,
            AuthCleanupProperties properties
    ) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(cron = "${eparly.security.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public CleanupReport runScheduledCleanup() {
        if (!properties.enabled()) {
            LOGGER.debug("Scheduled authentication data cleanup is disabled");
            return new CleanupReport(0, 0, 0, 0);
        }
        return cleanupAll();
    }

    @Transactional
    public CleanupReport cleanupAll() {
        Instant now = clock.instant();

        // 1. Jetons de vérification d'email expirés au-delà de la durée de rétention
        Instant emailCutoff = now.minus(properties.emailTokenRetention());
        int deletedEmailTokens = emailVerificationTokenRepository.deleteExpiredBefore(emailCutoff);

        // 2. Jetons de réinitialisation de mot de passe expirés au-delà de la durée de rétention
        Instant resetCutoff = now.minus(properties.passwordResetTokenRetention());
        int deletedResetTokens = passwordResetTokenRepository.deleteExpiredBefore(resetCutoff);

        // 3. Anciennes sessions de refresh tokens au-delà de la rétention (fenêtre anti-rejeu dépassée)
        Instant refreshCutoff = now.minus(properties.refreshTokenRetention());
        refreshTokenSessionRepository.clearReplacedByForExpiredBefore(refreshCutoff);
        int deletedRefreshSessions = refreshTokenSessionRepository.deleteExpiredBefore(refreshCutoff);

        // 4. Entrées de limitation de débit expirées
        int deletedRateLimits = rateLimiter.cleanupExpired(now);

        LOGGER.info("Auth data cleanup completed: emailTokens={}, resetTokens={}, refreshSessions={}, rateLimits={}",
                deletedEmailTokens, deletedResetTokens, deletedRefreshSessions, deletedRateLimits);

        return new CleanupReport(deletedEmailTokens, deletedResetTokens, deletedRefreshSessions, deletedRateLimits);
    }

    public record CleanupReport(
            int deletedEmailTokens,
            int deletedResetTokens,
            int deletedRefreshSessions,
            int deletedRateLimits
    ) {
    }
}
