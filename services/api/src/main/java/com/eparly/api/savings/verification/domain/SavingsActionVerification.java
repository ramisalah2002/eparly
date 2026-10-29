package com.eparly.api.savings.verification.domain;

import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.domain.VerificationStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "savings_action_verifications")
public class SavingsActionVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private SavingsAction action;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    @NotNull
    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "old_monthly_amount_minor")
    private Long oldMonthlyAmountMinor;

    @Column(name = "new_monthly_amount_minor")
    private Long newMonthlyAmountMinor;

    @Column(name = "observed_difference_minor")
    private Long observedDifferenceMinor;

    @Column(name = "covered_from")
    private LocalDate coveredFrom;

    @Column(name = "covered_to")
    private LocalDate coveredTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_information", columnDefinition = "jsonb")
    private List<String> missingInformation = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contradictions", columnDefinition = "jsonb")
    private List<String> contradictions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_reasons", columnDefinition = "jsonb")
    private List<String> decisionReasons = new ArrayList<>();

    public SavingsActionVerification() {
    }

    public SavingsActionVerification(SavingsAction action, User user, VerificationStatus status, Instant evaluatedAt,
                                     Long oldMonthlyAmountMinor, Long newMonthlyAmountMinor, Long observedDifferenceMinor,
                                     LocalDate coveredFrom, LocalDate coveredTo,
                                     List<String> missingInformation, List<String> contradictions, List<String> decisionReasons) {
        this.action = action;
        this.user = user;
        this.status = status;
        this.evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
        this.oldMonthlyAmountMinor = oldMonthlyAmountMinor;
        this.newMonthlyAmountMinor = newMonthlyAmountMinor;
        this.observedDifferenceMinor = observedDifferenceMinor;
        this.coveredFrom = coveredFrom;
        this.coveredTo = coveredTo;
        this.missingInformation = missingInformation != null ? new ArrayList<>(missingInformation) : new ArrayList<>();
        this.contradictions = contradictions != null ? new ArrayList<>(contradictions) : new ArrayList<>();
        this.decisionReasons = decisionReasons != null ? new ArrayList<>(decisionReasons) : new ArrayList<>();
    }

    @PrePersist
    protected void onCreate() {
        if (this.evaluatedAt == null) {
            this.evaluatedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SavingsAction getAction() { return action; }
    public void setAction(SavingsAction action) { this.action = action; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public Long getOldMonthlyAmountMinor() { return oldMonthlyAmountMinor; }
    public void setOldMonthlyAmountMinor(Long oldMonthlyAmountMinor) { this.oldMonthlyAmountMinor = oldMonthlyAmountMinor; }

    public Long getNewMonthlyAmountMinor() { return newMonthlyAmountMinor; }
    public void setNewMonthlyAmountMinor(Long newMonthlyAmountMinor) { this.newMonthlyAmountMinor = newMonthlyAmountMinor; }

    public Long getObservedDifferenceMinor() { return observedDifferenceMinor; }
    public void setObservedDifferenceMinor(Long observedDifferenceMinor) { this.observedDifferenceMinor = observedDifferenceMinor; }

    public LocalDate getCoveredFrom() { return coveredFrom; }
    public void setCoveredFrom(LocalDate coveredFrom) { this.coveredFrom = coveredFrom; }

    public LocalDate getCoveredTo() { return coveredTo; }
    public void setCoveredTo(LocalDate coveredTo) { this.coveredTo = coveredTo; }

    public List<String> getMissingInformation() { return missingInformation; }
    public void setMissingInformation(List<String> missingInformation) { this.missingInformation = missingInformation; }

    public List<String> getContradictions() { return contradictions; }
    public void setContradictions(List<String> contradictions) { this.contradictions = contradictions; }

    public List<String> getDecisionReasons() { return decisionReasons; }
    public void setDecisionReasons(List<String> decisionReasons) { this.decisionReasons = decisionReasons; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavingsActionVerification that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
