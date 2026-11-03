// Contrats Spring Boot repris de /v3/api-docs (services/api, migration V21).
// Ne pas élargir les énumérations côté front : seules ces valeurs existent dans l'API.

export type Sector = "STREAMING" | "MOBILE_PLAN" | "INTERNET_BOX";
export const SECTORS: Sector[] = ["STREAMING", "MOBILE_PLAN", "INTERNET_BOX"];

export type OfferStatus = "DRAFT" | "VERIFIED" | "STALE" | "EXPIRED" | "ARCHIVED";
export const OFFER_STATUSES: OfferStatus[] = ["DRAFT", "VERIFIED", "STALE", "EXPIRED", "ARCHIVED"];

export type ProviderStatus = "ACTIVE" | "INACTIVE";
export type BillingPeriod = "WEEKLY" | "MONTHLY" | "QUARTERLY" | "YEARLY" | "UNKNOWN";
export type PromotionPriceType = "PERCENTAGE" | "FIXED_PRICE";
export type EvidenceScope = "OFFER" | "PRICING" | "PROMOTION";
export type EvidenceStatus = "PENDING" | "VERIFIED" | "EXPIRED" | "INVALID";

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  message?: string;
  path?: string;
}

export interface AdminPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OfferProvider {
  id: string;
  name: string;
  sector: Sector;
  officialWebsite: string | null;
  country: string | null;
  status: ProviderStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface OfferPricing {
  id: string;
  amountMinor: number | null;
  currency: string | null;
  billingPeriod: BillingPeriod | null;
  activationFeeMinor: number | null;
  otherFees: string | null;
  promotionPercent: number | null;
  promotionDurationMonths: number | null;
  priceAfterPromotionMinor: number | null;
  discountConditions: string | null;
  taxesIncluded: boolean | null;
  dataUnknown: boolean | null;
  createdAt: string;
  updatedAt: string;
  promotionPriceType: PromotionPriceType | null;
  promotionValidFrom: string | null;
  promotionValidUntil: string | null;
  promotionVerified: boolean | null;
  version: number;
}

/** Preuve telle qu'embarquée dans CommercialOfferResponse (sans traçabilité du validateur). */
export interface OfferEvidence {
  id: string;
  officialUrl: string | null;
  consultedAt: string | null;
  verifiedAt: string | null;
  evidenceScope: EvidenceScope | null;
  nature: string | null;
  verifiedSummary: string | null;
  expiresAt: string | null;
  verificationStatus: EvidenceStatus;
  provenance: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Preuve renvoyée par GET /offers/{id}/evidences, avec les versions vérifiées. */
export interface AdminEvidence extends OfferEvidence {
  verifiedByUserId: string | null;
  verifiedPricingVersion: number | null;
  verifiedProviderVersion: number | null;
  version: number;
}

export interface CommercialOffer {
  id: string;
  provider: OfferProvider;
  commercialName: string;
  sector: Sector;
  characteristics: string | null;
  eligibilityConditions: string | null;
  commitment: string | null;
  territory: string | null;
  publicationStatus: OfferStatus;
  validFrom: string | null;
  validUntil: string | null;
  pricing: OfferPricing | null;
  evidence: OfferEvidence[] | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface OfferPriceHistory {
  id: string;
  oldAmountMinor: number | null;
  newAmountMinor: number | null;
  oldPricingSnapshot: Record<string, unknown> | null;
  newPricingSnapshot: Record<string, unknown> | null;
  changedAt: string;
  reason: string | null;
}

export interface CatalogAudit {
  id: string;
  actorUserId: string | null;
  action: string;
  resourceType: string;
  resourceId: string;
  details: Record<string, unknown> | null;
  occurredAt: string;
}

export interface CatalogSyncReport {
  id: string;
  startedAt: string | null;
  finishedAt: string | null;
  status: string | null;
  sourcesConfigured: number;
  sourcesProcessed: number;
  pagesFetched: number;
  pagesUnsupported: number;
  offersDiscovered: number;
  offersUpdated: number;
  offersUnchanged: number;
  offersRejected: number;
  errorsCount: number;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string | null;
  status: "PENDING_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DELETED";
}

/** Identité minimale exposée aux composants : jamais de jeton. */
export interface AdminIdentity {
  id: string;
  email: string;
  fullName: string | null;
}
