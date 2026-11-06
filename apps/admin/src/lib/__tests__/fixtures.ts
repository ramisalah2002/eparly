import type { AdminEvidence, CommercialOffer, OfferPricing } from "@/lib/types";

export function pricing(overrides: Partial<OfferPricing> = {}): OfferPricing {
  return {
    id: "00000000-0000-0000-0000-000000000101",
    amountMinor: 1999,
    currency: "EUR",
    billingPeriod: "MONTHLY",
    activationFeeMinor: null,
    otherFees: null,
    promotionPercent: null,
    promotionDurationMonths: null,
    priceAfterPromotionMinor: null,
    discountConditions: null,
    taxesIncluded: true,
    dataUnknown: false,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    promotionPriceType: null,
    promotionValidFrom: null,
    promotionValidUntil: null,
    promotionVerified: null,
    version: 3,
    ...overrides,
  };
}

export function offer(overrides: Partial<CommercialOffer> = {}): CommercialOffer {
  return {
    id: "00000000-0000-0000-0000-000000000001",
    provider: {
      id: "00000000-0000-0000-0000-000000000010",
      name: "Fournisseur de test",
      sector: "MOBILE_PLAN",
      officialWebsite: "https://provider.example.test",
      country: "FR",
      status: "ACTIVE",
      createdAt: "2026-10-01T10:00:00Z",
      updatedAt: "2026-10-01T10:00:00Z",
      version: 2,
    },
    commercialName: "Forfait de test",
    sector: "MOBILE_PLAN",
    characteristics: null,
    eligibilityConditions: null,
    commitment: "Sans engagement",
    territory: "FR",
    publicationStatus: "DRAFT",
    validFrom: null,
    validUntil: null,
    pricing: pricing(),
    evidence: [],
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-02T10:00:00Z",
    version: 1,
    ...overrides,
  };
}

export function adminEvidence(overrides: Partial<AdminEvidence> = {}): AdminEvidence {
  return {
    id: "00000000-0000-0000-0000-000000000201",
    officialUrl: "https://provider.example.test/offres/forfait",
    consultedAt: "2026-10-20T09:00:00Z",
    verifiedAt: "2026-10-20T09:05:00Z",
    evidenceScope: "OFFER",
    nature: "Tarif et conditions",
    verifiedSummary: "Prix et conditions constatés sur la page officielle.",
    expiresAt: null,
    verificationStatus: "VERIFIED",
    provenance: "Consultation humaine",
    createdAt: "2026-10-20T08:00:00Z",
    updatedAt: "2026-10-20T09:05:00Z",
    verifiedByUserId: "00000000-0000-0000-0000-000000000999",
    verifiedPricingVersion: 3,
    verifiedProviderVersion: 2,
    version: 4,
    ...overrides,
  };
}
