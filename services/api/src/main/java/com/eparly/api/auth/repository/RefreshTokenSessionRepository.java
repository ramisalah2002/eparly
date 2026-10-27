package com.eparly.api.auth.repository;

import com.eparly.api.auth.domain.RefreshTokenSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshTokenSession session join fetch session.user " +
            "where session.tokenHash = :tokenHash")
    Optional<RefreshTokenSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshTokenSession session set session.revokedAt = :revokedAt, " +
            "session.revokedReason = :reason where session.familyId = :familyId " +
            "and session.revokedAt is null")
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("update RefreshTokenSession session set session.revokedAt = :revokedAt, " +
            "session.revokedReason = :reason where session.user.id = :userId " +
            "and session.revokedAt is null")
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("update RefreshTokenSession s set s.replacedBySessionId = null where s.expiresAt < :cutoff")
    int clearReplacedByForExpiredBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from RefreshTokenSession s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
