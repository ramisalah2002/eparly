package com.eparly.api.auth.domain;

import com.eparly.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token_sessions", uniqueConstraints =
        @UniqueConstraint(name = "uq_refresh_token_sessions_token_hash", columnNames = "token_hash"))
public class RefreshTokenSession {
    private UUID sessionId;
    private User user;
    private String tokenHash;
    private UUID familyId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant consumedAt;
    private UUID replacedBySessionId;
    private Instant revokedAt;
    private String revokedReason;

    protected RefreshTokenSession() {
    }

    public RefreshTokenSession(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    @Column(name = "family_id", nullable = false, updatable = false)
    public UUID getFamilyId() {
        return familyId;
    }

    public void setFamilyId(UUID familyId) {
        this.familyId = familyId;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Column(name = "expires_at", nullable = false, updatable = false)
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Column(name = "consumed_at")
    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    @Column(name = "replaced_by_session_id")
    public UUID getReplacedBySessionId() {
        return replacedBySessionId;
    }

    public void setReplacedBySessionId(UUID replacedBySessionId) {
        this.replacedBySessionId = replacedBySessionId;
    }

    @Column(name = "revoked_at")
    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    @Column(name = "revoked_reason", length = 32)
    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (sessionId == null) {
            sessionId = UUID.randomUUID();
        }
    }

    @Transient
    public boolean isConsumed() {
        return consumedAt != null;
    }

    @Transient
    public boolean isRevoked() {
        return revokedAt != null;
    }
}
