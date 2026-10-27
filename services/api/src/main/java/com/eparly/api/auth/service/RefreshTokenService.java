package com.eparly.api.auth.service;

import com.eparly.api.auth.config.RefreshTokenProperties;
import com.eparly.api.auth.domain.RefreshTokenSession;
import com.eparly.api.auth.dto.TokenResponse;
import com.eparly.api.auth.repository.RefreshTokenSessionRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final String LOGOUT = "LOGOUT";
    private static final String TOKEN_REUSE = "TOKEN_REUSE";
    private static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    private static final String EXPIRED = "EXPIRED";
    private static final String PASSWORD_RESET = "PASSWORD_RESET";

    private final RefreshTokenSessionRepository sessionRepository;
    private final RefreshTokenProperties properties;
    private final AccessTokenService accessTokenService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public RefreshTokenService(
            RefreshTokenSessionRepository sessionRepository,
            RefreshTokenProperties properties,
            AccessTokenService accessTokenService
    ) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    @Transactional
    public TokenResponse createSession(User user) {
        String rawToken = generateToken();
        Instant expiresAt = clock.instant().plus(properties.ttl());
        UUID familyId = UUID.randomUUID();
        sessionRepository.saveAndFlush(new RefreshTokenSession(
                user, hashToken(rawToken), familyId, expiresAt
        ));
        return tokenResponse(user, rawToken, expiresAt);
    }

    /**
     * The caller returns an error only after this transaction commits, so TOKEN_REUSE
     * family revocation cannot be rolled back by the exception response.
     */
    @Transactional
    public Optional<TokenResponse> rotate(String rawToken) {
        Optional<RefreshTokenSession> found =
                sessionRepository.findByTokenHashForUpdate(hashToken(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshTokenSession current = found.get();
        Instant now = clock.instant();
        if (current.isConsumed()) {
            revokeFamily(current, now, TOKEN_REUSE);
            return Optional.empty();
        }
        if (current.isRevoked()) {
            return Optional.empty();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            revokeFamily(current, now, EXPIRED);
            return Optional.empty();
        }
        User user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            revokeFamily(current, now, ACCOUNT_DISABLED);
            return Optional.empty();
        }

        String nextRawToken = generateToken();
        RefreshTokenSession next = new RefreshTokenSession(
                user, hashToken(nextRawToken), current.getFamilyId(), current.getExpiresAt()
        );

        current.setConsumedAt(now);
        sessionRepository.saveAndFlush(current);
        next = sessionRepository.saveAndFlush(next);
        current.setReplacedBySessionId(next.getSessionId());
        sessionRepository.save(current);

        return Optional.of(tokenResponse(user, nextRawToken, current.getExpiresAt()));
    }

    @Transactional
    public void logout(String rawToken) {
        sessionRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .ifPresent(session -> {
                    if (!session.isRevoked()) {
                        revokeFamily(session, clock.instant(), LOGOUT);
                    }
                });
    }

    @Transactional
    public void revokeAllUserSessions(UUID userId) {
        sessionRepository.revokeAllForUser(userId, clock.instant(), PASSWORD_RESET);
    }

    private void revokeFamily(RefreshTokenSession session, Instant at, String reason) {
        sessionRepository.revokeFamily(session.getFamilyId(), at, reason);
    }

    private TokenResponse tokenResponse(User user, String rawRefreshToken, Instant refreshExpiresAt) {
        return new TokenResponse(
                accessTokenService.create(user),
                "Bearer",
                accessTokenService.expiresInSeconds(),
                rawRefreshToken,
                Math.max(0, refreshExpiresAt.getEpochSecond() - clock.instant().getEpochSecond())
        );
    }

    private String generateToken() {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
