package com.eparly.api.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "offer_price_history")
public class OfferPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private CommercialOffer offer;

    @Column(name = "old_amount_minor")
    private Long oldAmountMinor;

    @Column(name = "new_amount_minor")
    private Long newAmountMinor;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    public OfferPriceHistory() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) this.changedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CommercialOffer getOffer() { return offer; }
    public void setOffer(CommercialOffer offer) { this.offer = offer; }

    public Long getOldAmountMinor() { return oldAmountMinor; }
    public void setOldAmountMinor(Long oldAmountMinor) { this.oldAmountMinor = oldAmountMinor; }

    public Long getNewAmountMinor() { return newAmountMinor; }
    public void setNewAmountMinor(Long newAmountMinor) { this.newAmountMinor = newAmountMinor; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfferPriceHistory h)) return false;
        return id != null && Objects.equals(id, h.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
