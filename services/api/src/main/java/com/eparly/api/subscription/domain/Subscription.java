package com.eparly.api.subscription.domain;

import com.eparly.api.account.domain.Account;
import com.eparly.api.recurring.domain.RecurringFrequency;
import com.eparly.api.recurring.domain.RecurringSeries;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "recurring_series_id", unique = true)
    private RecurringSeries recurringSeries;

    @NotBlank
    @Size(max = 200)
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 200)
    @Column(name = "provider", length = 200)
    private String provider;

    @Column(name = "declared_amount_minor")
    private Long declaredAmountMinor;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "declared_frequency", nullable = false, length = 20)
    private RecurringFrequency declaredFrequency = RecurringFrequency.UNKNOWN;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @NotNull
    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Size(max = 1000)
    @Column(name = "notes", length = 1000)
    private String notes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "declared_status", nullable = false, length = 20)
    private SubscriptionStatus declaredStatus = SubscriptionStatus.ACTIVE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private SubscriptionOrigin origin = SubscriptionOrigin.USER_DECLARED;

    @NotNull
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Subscription() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.confirmedAt == null) this.confirmedAt = now;
        if (this.statusChangedAt == null) this.statusChangedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public RecurringSeries getRecurringSeries() { return recurringSeries; }
    public void setRecurringSeries(RecurringSeries recurringSeries) { this.recurringSeries = recurringSeries; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Long getDeclaredAmountMinor() { return declaredAmountMinor; }
    public void setDeclaredAmountMinor(Long declaredAmountMinor) { this.declaredAmountMinor = declaredAmountMinor; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public RecurringFrequency getDeclaredFrequency() { return declaredFrequency; }
    public void setDeclaredFrequency(RecurringFrequency declaredFrequency) { this.declaredFrequency = declaredFrequency; }

    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public SubscriptionStatus getDeclaredStatus() { return declaredStatus; }
    public void setDeclaredStatus(SubscriptionStatus declaredStatus) { this.declaredStatus = declaredStatus; }

    public SubscriptionOrigin getOrigin() { return origin; }
    public void setOrigin(SubscriptionOrigin origin) { this.origin = origin; }

    public Instant getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subscription s)) return false;
        return id != null && Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
