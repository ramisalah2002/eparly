export type OfferSector = "STREAMING" | "MOBILE_PLAN" | "INTERNET_BOX";
export type OfferStatus = "DRAFT" | "VERIFIED" | "STALE" | "EXPIRED" | "ARCHIVED";
export type EvidenceStatus = "PENDING" | "VERIFIED" | "EXPIRED" | "INVALID";
export type OfferBillingPeriod = "WEEKLY" | "MONTHLY" | "QUARTERLY" | "YEARLY" | "UNKNOWN";
export type OfferEvidenceScope = "OFFER" | "PRICING" | "PROMOTION";
export type PromotionPriceType = "PERCENTAGE" | "FIXED_PRICE";
export type ProviderStatus = "ACTIVE" | "INACTIVE";

export interface OfferProviderResponse {
  id: string;
  name: string;
  sector: OfferSector;
  officialWebsite: string;
  country: string;
  status: ProviderStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface OfferPricingResponse {
  id: string;
  amountMinor: number | null;
  currency: string | null;
  billingPeriod: OfferBillingPeriod | null;
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
  promotionVerified: boolean;
  version: number;
}

export interface OfferEvidenceResponse {
  id: string;
  officialUrl: string;
  consultedAt: string | null;
  verifiedAt: string | null;
  evidenceScope: OfferEvidenceScope;
  nature: string;
  verifiedSummary: string | null;
  expiresAt: string | null;
  verificationStatus: EvidenceStatus;
  provenance: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CommercialOfferResponse {
  id: string;
  provider: OfferProviderResponse;
  commercialName: string;
  sector: OfferSector;
  characteristics: string | null;
  eligibilityConditions: string | null;
  commitment: string | null;
  territory: string | null;
  publicationStatus: OfferStatus;
  validFrom: string | null;
  validUntil: string | null;
  pricing: OfferPricingResponse | null;
  evidence: OfferEvidenceResponse[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface AdminEvidenceResponse extends OfferEvidenceResponse {
  verifiedByUserId: string | null;
  verifiedPricingVersion: number | null;
  verifiedProviderVersion: number | null;
  version: number;
}

export interface OfferPriceHistoryResponse {
  id: string;
  oldAmountMinor: number | null;
  newAmountMinor: number | null;
  oldPricingSnapshot: Record<string, unknown> | null;
  newPricingSnapshot: Record<string, unknown> | null;
  changedAt: string;
  reason: string | null;
}

export interface CatalogAuditResponse {
  id: string;
  actorUserId: string | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  details: Record<string, unknown> | null;
  occurredAt: string;
}

export interface AdminPageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OfferListItem {
  id: string;
  providerId: string;
  providerName: string;
  commercialName: string;
  sector: OfferSector;
  territory: string | null;
  publicationStatus: OfferStatus;
  amountMinor: number | null;
  currency: string | null;
  billingPeriod: string | null;
  validFrom: string | null;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OfferListResponse {
  content: OfferListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OfferMetadataResponse {
  sectors: string[];
  billingPeriods: string[];
  publicStatuses: string[];
  territories: string[];
  defaultCurrency: string;
  amountsInMinorUnits: boolean;
  coverageNote: string;
}
