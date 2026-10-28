package com.eparly.api.recurring.domain;

import com.eparly.api.account.domain.Account;
import com.eparly.api.transaction.domain.Transaction;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "recurring_series")
public class RecurringSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotBlank
    @Size(max = 500)
    @Column(name = "normalized_label", nullable = false, length = 500)
    private String normalizedLabel;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency = RecurringFrequency.UNKNOWN;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecurringSeriesStatus status = RecurringSeriesStatus.CANDIDATE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "series_type", nullable = false, length = 30)
    private RecurringSeriesType seriesType = RecurringSeriesType.UNCLASSIFIED;

    @NotNull
    @Column(name = "usual_amount_minor", nullable = false)
    private Long usualAmountMinor;

    @NotNull
    @Column(name = "min_amount_minor", nullable = false)
    private Long minAmountMinor;

    @NotNull
    @Column(name = "max_amount_minor", nullable = false)
    private Long maxAmountMinor;

    @NotNull
    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount = 0;

    @NotNull
    @Column(name = "first_occurrence_date", nullable = false)
    private LocalDate firstOccurrenceDate;

    @NotNull
    @Column(name = "last_occurrence_date", nullable = false)
    private LocalDate lastOccurrenceDate;

    @Column(name = "next_estimated_date")
    private LocalDate nextEstimatedDate;

    @Column(name = "monthly_equivalent_minor", precision = 19, scale = 4)
    private BigDecimal monthlyEquivalentMinor;

    @NotNull
    @Column(name = "confidence_score", nullable = false)
    private Integer confidenceScore = 0;

    @NotNull
    @Column(name = "dismissed_occurrence_count", nullable = false)
    private Integer dismissedOccurrenceCount = 0;

    @NotNull
    @Column(name = "last_analyzed_at", nullable = false)
    private Instant lastAnalyzedAt;

    @Column(name = "user_decision_at")
    private Instant userDecisionAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_series_reasons", joinColumns = @JoinColumn(name = "series_id"))
    @OrderColumn(name = "position")
    @Column(name = "reason", length = 500, nullable = false)
    private List<String> reasons = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "recurring_series_transactions",
            joinColumns = @JoinColumn(name = "series_id"),
            inverseJoinColumns = @JoinColumn(name = "transaction_id")
    )
    private Set<Transaction> transactions = new LinkedHashSet<>();

    public RecurringSeries() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.lastAnalyzedAt == null) this.lastAnalyzedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public String getNormalizedLabel() { return normalizedLabel; }
    public void setNormalizedLabel(String normalizedLabel) { this.normalizedLabel = normalizedLabel; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public RecurringFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurringFrequency frequency) { this.frequency = frequency; }

    public RecurringSeriesStatus getStatus() { return status; }
    public void setStatus(RecurringSeriesStatus status) { this.status = status; }

    public RecurringSeriesType getSeriesType() { return seriesType; }
    public void setSeriesType(RecurringSeriesType seriesType) { this.seriesType = seriesType; }

    public Long getUsualAmountMinor() { return usualAmountMinor; }
    public void setUsualAmountMinor(Long usualAmountMinor) { this.usualAmountMinor = usualAmountMinor; }

    public Long getMinAmountMinor() { return minAmountMinor; }
    public void setMinAmountMinor(Long minAmountMinor) { this.minAmountMinor = minAmountMinor; }

    public Long getMaxAmountMinor() { return maxAmountMinor; }
    public void setMaxAmountMinor(Long maxAmountMinor) { this.maxAmountMinor = maxAmountMinor; }

    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public LocalDate getFirstOccurrenceDate() { return firstOccurrenceDate; }
    public void setFirstOccurrenceDate(LocalDate firstOccurrenceDate) { this.firstOccurrenceDate = firstOccurrenceDate; }

    public LocalDate getLastOccurrenceDate() { return lastOccurrenceDate; }
    public void setLastOccurrenceDate(LocalDate lastOccurrenceDate) { this.lastOccurrenceDate = lastOccurrenceDate; }

    public LocalDate getNextEstimatedDate() { return nextEstimatedDate; }
    public void setNextEstimatedDate(LocalDate nextEstimatedDate) { this.nextEstimatedDate = nextEstimatedDate; }

    public BigDecimal getMonthlyEquivalentMinor() { return monthlyEquivalentMinor; }
    public void setMonthlyEquivalentMinor(BigDecimal monthlyEquivalentMinor) { this.monthlyEquivalentMinor = monthlyEquivalentMinor; }

    public Integer getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Integer confidenceScore) { this.confidenceScore = confidenceScore; }

    public Integer getDismissedOccurrenceCount() { return dismissedOccurrenceCount; }
    public void setDismissedOccurrenceCount(Integer dismissedOccurrenceCount) { this.dismissedOccurrenceCount = dismissedOccurrenceCount; }

    public Instant getLastAnalyzedAt() { return lastAnalyzedAt; }
    public void setLastAnalyzedAt(Instant lastAnalyzedAt) { this.lastAnalyzedAt = lastAnalyzedAt; }

    public Instant getUserDecisionAt() { return userDecisionAt; }
    public void setUserDecisionAt(Instant userDecisionAt) { this.userDecisionAt = userDecisionAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public Set<Transaction> getTransactions() { return transactions; }
    public void setTransactions(Set<Transaction> transactions) { this.transactions = transactions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringSeries s)) return false;
        return id != null && Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
