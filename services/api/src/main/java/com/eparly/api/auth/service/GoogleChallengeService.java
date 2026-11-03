package com.eparly.api.auth.service;

import com.eparly.api.auth.config.GoogleAuthProperties;
import com.eparly.api.auth.domain.GoogleChallengePurpose;
import com.eparly.api.auth.dto.GoogleChallengeResponse;
import com.eparly.api.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Challenges a usage unique : le nonce n'est rendu qu'une fois au client, seule son empreinte est stockee.
 * La consommation repose sur un UPDATE conditionnel : PostgreSQL ne laisse gagner qu'une requete concurrente.
 */
@Service
public class GoogleChallengeService {
    public static final String CHALLENGE_INVALID = "GOOGLE_CHALLENGE_INVALID";
    private static final int NONCE_BYTES = 32;

    private final JdbcTemplate jdbc;
    private final GoogleAuthProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public GoogleChallengeService(JdbcTemplate jdbc, GoogleAuthProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public GoogleChallengeResponse create(GoogleChallengePurpose purpose, UUID userId) {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.challengeTtl());
        jdbc.update("""
                INSERT INTO google_auth_challenges (id, nonce_hash, purpose, user_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, hash(nonce), purpose.name(), userId, Timestamp.from(now), Timestamp.from(expiresAt));
        return new GoogleChallengeResponse(id, nonce, purpose, expiresAt);
    }

    /** Refuse un challenge inconnu, expire, consomme, d'une autre intention ou d'un autre utilisateur. */
    public String requireUsable(UUID id, GoogleChallengePurpose purpose, UUID userId) {
        List<String> hashes = jdbc.queryForList("""
                SELECT nonce_hash FROM google_auth_challenges
                WHERE id = ? AND purpose = ? AND user_id IS NOT DISTINCT FROM ?::uuid
                  AND consumed_at IS NULL AND expires_at > ?
                """, String.class, id, purpose.name(), userId, Timestamp.from(clock.instant()));
        if (hashes.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, CHALLENGE_INVALID);
        return hashes.get(0);
    }

    public void requireNonce(String expectedHash, String tokenNonce) {
        boolean valid = tokenNonce != null && MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8), hash(tokenNonce).getBytes(StandardCharsets.UTF_8));
        if (!valid) throw new ApiException(HttpStatus.UNAUTHORIZED, GoogleIdTokenVerifier.INVALID_IDENTITY);
    }

    /** A appeler dans la transaction du compte : un echec metier (409, 428) annule aussi la consommation. */
    public void consume(UUID id, GoogleChallengePurpose purpose, UUID userId) {
        Instant now = clock.instant();
        int updated = jdbc.update("""
                UPDATE google_auth_challenges SET consumed_at = ?
                WHERE id = ? AND purpose = ? AND user_id IS NOT DISTINCT FROM ?::uuid
                  AND consumed_at IS NULL AND expires_at > ?
                """, Timestamp.from(now), id, purpose.name(), userId, Timestamp.from(now));
        if (updated != 1) throw new ApiException(HttpStatus.UNAUTHORIZED, CHALLENGE_INVALID);
    }

    public int cleanupExpired(Instant cutoff) {
        return jdbc.update("DELETE FROM google_auth_challenges WHERE expires_at < ?", Timestamp.from(cutoff));
    }

    private static String hash(String nonce) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
