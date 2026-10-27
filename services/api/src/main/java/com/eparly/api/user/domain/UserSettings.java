package com.eparly.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Préférences financières et régionales associées à un utilisateur Eparly.
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @NotNull
    @Column(name = "country", nullable = false, length = 2)
    private String country = "FR";

    @NotNull
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Europe/Paris";

    @NotNull
    @PositiveOrZero
    @Column(name = "monthly_savings_target_cents", nullable = false)
    private Long monthlySavingsTargetCents = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserSettings() {
    }

    public UserSettings(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Long getMonthlySavingsTargetCents() {
        return monthlySavingsTargetCents;
    }

    public void setMonthlySavingsTargetCents(Long monthlySavingsTargetCents) {
        this.monthlySavingsTargetCents = monthlySavingsTargetCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSettings that)) return false;
        return userId != null && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "UserSettings{" +
                "userId=" + userId +
                ", currency='" + currency + '\'' +
                ", country='" + country + '\'' +
                ", timezone='" + timezone + '\'' +
                ", monthlySavingsTargetCents=" + monthlySavingsTargetCents +
                '}';
    }
}
