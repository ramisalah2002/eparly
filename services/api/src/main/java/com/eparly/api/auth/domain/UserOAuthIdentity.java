package com.eparly.api.auth.domain;

import com.eparly.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Lien entre un compte Eparly et une identite externe. Aucun jeton du fournisseur n'est conserve. */
@Entity
@Table(name = "user_oauth_identities")
public class UserOAuthIdentity {
    public static final String GOOGLE = "GOOGLE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "email_at_link_time", nullable = false, length = 255)
    private String emailAtLinkTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserOAuthIdentity() {
    }

    public UserOAuthIdentity(User user, String provider, String providerSubject, String emailAtLinkTime) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.emailAtLinkTime = emailAtLinkTime;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public String getEmailAtLinkTime() {
        return emailAtLinkTime;
    }
}
