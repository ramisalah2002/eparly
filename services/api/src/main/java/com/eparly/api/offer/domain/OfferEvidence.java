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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "offer_evidence")
public class OfferEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private CommercialOffer offer;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "official_url", nullable = false, length = 1000)
    private String officialUrl;

    @Column(name = "consulted_at")
    private Instant consultedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_pricing_version")
    private Long verifiedPricingVersion;

    @Column(name = "verified_provider_version")
    private Long verifiedProviderVersion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_scope", length = 20)
    // Null remains the legacy whole-offer scope; new evidence should state its scope.
    private OfferEvidenceScope evidenceScope;

    @NotBlank
    @Size(max = 200)
    @Column(name = "nature", nullable = false, length = 200)
    private String nature;

    @Size(max = 2000)
    @Column(name = "verified_summary", length = 2000)
    private String verifiedSummary;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private EvidenceStatus verificationStatus = EvidenceStatus.PENDING;

    @NotBlank
    @Size(max = 200)
    @Column(name = "provenance", nullable = false, length = 200)
    private String provenance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OfferEvidence() {
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

    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) { this.officialUrl = officialUrl; }

    public Instant getConsultedAt() { return consultedAt; }
    public void setConsultedAt(Instant consultedAt) { this.consultedAt = consultedAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public UUID getVerifiedByUserId() { return verifiedByUserId; }
    public void setVerifiedByUserId(UUID verifiedByUserId) { this.verifiedByUserId = verifiedByUserId; }
    public Long getVerifiedPricingVersion() { return verifiedPricingVersion; }
    public void setVerifiedPricingVersion(Long verifiedPricingVersion) {
        this.verifiedPricingVersion = verifiedPricingVersion;
    }
    public Long getVerifiedProviderVersion() { return verifiedProviderVersion; }
    public void setVerifiedProviderVersion(Long verifiedProviderVersion) {
        this.verifiedProviderVersion = verifiedProviderVersion;
    }
    public Long getVersion() { return version; }

    public OfferEvidenceScope getEvidenceScope() { return evidenceScope; }
    public void setEvidenceScope(OfferEvidenceScope evidenceScope) { this.evidenceScope = evidenceScope; }

    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }

    public String getVerifiedSummary() { return verifiedSummary; }
    public void setVerifiedSummary(String verifiedSummary) { this.verifiedSummary = verifiedSummary; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public EvidenceStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(EvidenceStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfferEvidence e)) return false;
        return id != null && Objects.equals(id, e.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
