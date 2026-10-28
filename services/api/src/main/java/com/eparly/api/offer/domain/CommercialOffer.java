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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "commercial_offers")
public class CommercialOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private OfferProvider provider;

    @NotBlank
    @Size(max = 200)
    @Column(name = "commercial_name", nullable = false, length = 200)
    private String commercialName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sector", nullable = false, length = 30)
    private OfferSector sector;

    @Column(name = "characteristics", columnDefinition = "text")
    private String characteristics;

    @Column(name = "eligibility_conditions", columnDefinition = "text")
    private String eligibilityConditions;

    @Size(max = 100)
    @Column(name = "commitment", length = 100)
    private String commitment;

    @NotBlank
    @Size(max = 100)
    @Column(name = "territory", nullable = false, length = 100)
    private String territory = "FR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private OfferStatus publicationStatus = OfferStatus.DRAFT;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CommercialOffer() {
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

    public OfferProvider getProvider() { return provider; }
    public void setProvider(OfferProvider provider) { this.provider = provider; }

    public String getCommercialName() { return commercialName; }
    public void setCommercialName(String commercialName) { this.commercialName = commercialName; }

    public OfferSector getSector() { return sector; }
    public void setSector(OfferSector sector) { this.sector = sector; }

    public String getCharacteristics() { return characteristics; }
    public void setCharacteristics(String characteristics) { this.characteristics = characteristics; }

    public String getEligibilityConditions() { return eligibilityConditions; }
    public void setEligibilityConditions(String eligibilityConditions) { this.eligibilityConditions = eligibilityConditions; }

    public String getCommitment() { return commitment; }
    public void setCommitment(String commitment) { this.commitment = commitment; }

    public String getTerritory() { return territory; }
    public void setTerritory(String territory) { this.territory = territory; }

    public OfferStatus getPublicationStatus() { return publicationStatus; }
    public void setPublicationStatus(OfferStatus publicationStatus) { this.publicationStatus = publicationStatus; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommercialOffer o2)) return false;
        return id != null && Objects.equals(id, o2.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
