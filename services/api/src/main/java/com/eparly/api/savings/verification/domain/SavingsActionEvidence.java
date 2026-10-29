package com.eparly.api.savings.verification.domain;

import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.transaction.domain.Transaction;
import com.eparly.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "savings_action_evidences")
public class SavingsActionEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private SavingsAction action;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private EvidenceRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 30)
    private EvidenceProvenance provenance = EvidenceProvenance.USER_CONFIRMED;

    @NotNull
    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SavingsActionEvidence() {
    }

    public SavingsActionEvidence(User user, SavingsAction action, Transaction transaction, EvidenceRole role,
                                 EvidenceProvenance provenance, Instant confirmedAt) {
        this.user = user;
        this.action = action;
        this.transaction = transaction;
        this.role = role;
        this.provenance = provenance != null ? provenance : EvidenceProvenance.USER_CONFIRMED;
        this.confirmedAt = confirmedAt != null ? confirmedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.confirmedAt == null) this.confirmedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public SavingsAction getAction() { return action; }
    public void setAction(SavingsAction action) { this.action = action; }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public EvidenceRole getRole() { return role; }
    public void setRole(EvidenceRole role) { this.role = role; }

    public EvidenceProvenance getProvenance() { return provenance; }
    public void setProvenance(EvidenceProvenance provenance) { this.provenance = provenance; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavingsActionEvidence e)) return false;
        return id != null && Objects.equals(id, e.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
