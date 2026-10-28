package com.eparly.api.offer.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "offer_pricing")
public class OfferPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private CommercialOffer offer;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 20)
    private OfferBillingPeriod billingPeriod = OfferBillingPeriod.MONTHLY;

    @Column(name = "activation_fee_minor")
    private Long activationFeeMinor;

    @Column(name = "other_fees", columnDefinition = "text")
    private String otherFees;

    @Column(name = "promotion_percent")
    private Integer promotionPercent;

    @Column(name = "promotion_duration_months")
    private Integer promotionDurationMonths;

    @Column(name = "price_after_promotion_minor")
    private Long priceAfterPromotionMinor;

    @Size(max = 500)
    @Column(name = "discount_conditions", length = 500)
    private String discountConditions;

    @Column(name = "taxes_included", nullable = false)
    private Boolean taxesIncluded = true;

    @Column(name = "data_unknown", nullable = false)
    private Boolean dataUnknown = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OfferPricing() {
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

    public CommercialOffer getOffer() { return offer; }
    public void setOffer(CommercialOffer offer) { this.offer = offer; }

    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public OfferBillingPeriod getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(OfferBillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }

    public Long getActivationFeeMinor() { return activationFeeMinor; }
    public void setActivationFeeMinor(Long activationFeeMinor) { this.activationFeeMinor = activationFeeMinor; }

    public String getOtherFees() { return otherFees; }
    public void setOtherFees(String otherFees) { this.otherFees = otherFees; }

    public Integer getPromotionPercent() { return promotionPercent; }
    public void setPromotionPercent(Integer promotionPercent) { this.promotionPercent = promotionPercent; }

    public Integer getPromotionDurationMonths() { return promotionDurationMonths; }
    public void setPromotionDurationMonths(Integer promotionDurationMonths) { this.promotionDurationMonths = promotionDurationMonths; }

    public Long getPriceAfterPromotionMinor() { return priceAfterPromotionMinor; }
    public void setPriceAfterPromotionMinor(Long priceAfterPromotionMinor) { this.priceAfterPromotionMinor = priceAfterPromotionMinor; }

    public String getDiscountConditions() { return discountConditions; }
    public void setDiscountConditions(String discountConditions) { this.discountConditions = discountConditions; }

    public Boolean getTaxesIncluded() { return taxesIncluded; }
    public void setTaxesIncluded(Boolean taxesIncluded) { this.taxesIncluded = taxesIncluded; }

    public Boolean getDataUnknown() { return dataUnknown; }
    public void setDataUnknown(Boolean dataUnknown) { this.dataUnknown = dataUnknown; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfferPricing p)) return false;
        return id != null && Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
