package com.eparly.api.savings.action.domain;

import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.savings.action.dto.SavingsEstimateSnapshot;
import com.eparly.api.subscription.domain.Subscription;
import com.eparly.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "savings_actions")
public class SavingsAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alternative_offer_id", nullable = false)
    private CommercialOffer alternativeOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActionStatus status = ActionStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.NOT_VERIFIED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "declared_at")
    private Instant declaredAt;

    @Column(name = "effective_change_date")
    private LocalDate effectiveChangeDate;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "horizon_months", nullable = false)
    private Integer horizonMonths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estimate_snapshot", columnDefinition = "jsonb")
    private SavingsEstimateSnapshot estimateSnapshot;

    public SavingsAction() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) { this.subscription = subscription; }

    public CommercialOffer getAlternativeOffer() { return alternativeOffer; }
    public void setAlternativeOffer(CommercialOffer alternativeOffer) { this.alternativeOffer = alternativeOffer; }

    public ActionStatus getStatus() { return status; }
    public void setStatus(ActionStatus status) { this.status = status; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCommittedAt() { return committedAt; }
    public void setCommittedAt(Instant committedAt) { this.committedAt = committedAt; }

    public Instant getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(Instant declaredAt) { this.declaredAt = declaredAt; }

    public LocalDate getEffectiveChangeDate() { return effectiveChangeDate; }
    public void setEffectiveChangeDate(LocalDate effectiveChangeDate) { this.effectiveChangeDate = effectiveChangeDate; }

    public Instant getCanceledAt() { return canceledAt; }
    public void setCanceledAt(Instant canceledAt) { this.canceledAt = canceledAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getHorizonMonths() { return horizonMonths; }
    public void setHorizonMonths(Integer horizonMonths) { this.horizonMonths = horizonMonths; }

    public SavingsEstimateSnapshot getEstimateSnapshot() { return estimateSnapshot; }
    public void setEstimateSnapshot(SavingsEstimateSnapshot estimateSnapshot) { this.estimateSnapshot = estimateSnapshot; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavingsAction a)) return false;
        return id != null && Objects.equals(id, a.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
