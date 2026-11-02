import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().email("Adresse email invalide"),
  password: z.string().min(1, "Le mot de passe est requis"),
});

export type LoginInput = z.infer<typeof loginSchema>;

export const offerPatchSchema = z.object({
  expectedVersion: z.string().min(1, "Version requise"),
  commercialName: z.string().min(1, "Nom commercial requis").max(200),
  characteristics: z.string().optional(),
  eligibilityConditions: z.string().optional(),
  commitment: z.string().max(100).optional().or(z.literal("")),
  territory: z.string().min(1, "Territoire requis").max(100),
  validFrom: z.string().optional().or(z.literal("")),
  validUntil: z.string().optional().or(z.literal("")),
});

export type OfferPatchInput = z.infer<typeof offerPatchSchema>;

export const pricingUpsertSchema = z.object({
  expectedVersion: z.string().optional(),
  amountMinor: z
    .string()
    .refine((val) => val === "" || /^\d+$/.test(val), "Montant invalide")
    .transform((val) => (val === "" ? undefined : Number(val))),
  currency: z.string().min(1, "Devise requise"),
  billingPeriod: z.enum(["WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "UNKNOWN"]),
  activationFeeMinor: z.string().optional().transform((val) => (val === "" ? undefined : Number(val))),
  otherFees: z.string().optional(),
  promotionPriceType: z.enum(["PERCENTAGE", "FIXED_PRICE"]).optional(),
  promotionPercent: z
    .string()
    .optional()
    .transform((val) => (val === "" ? undefined : Number(val))),
  promotionDurationMonths: z
    .string()
    .optional()
    .transform((val) => (val === "" ? undefined : Number(val))),
  priceAfterPromotionMinor: z
    .string()
    .optional()
    .transform((val) => (val === "" ? undefined : Number(val))),
  discountConditions: z.string().optional(),
  taxesIncluded: z.boolean(),
  dataUnknown: z.boolean(),
  reason: z.string().optional(),
});

export type PricingUpsertInput = z.infer<typeof pricingUpsertSchema>;

export const evidenceVerificationSchema = z.object({
  expectedVersion: z.string().min(1, "Version requise"),
  status: z.enum(["VERIFIED", "INVALID"]),
  summary: z.string().min(1, "Résumé requis").max(2000),
});

export type EvidenceVerificationInput = z.infer<typeof evidenceVerificationSchema>;

export const evidenceCreateSchema = z.object({
  officialUrl: z.string().url("URL invalide").min(1, "URL requise"),
  evidenceScope: z.enum(["OFFER", "PRICING", "PROMOTION"]),
  nature: z.string().min(1, "Nature requise").max(200),
  provenance: z.string().min(1, "Provenance requise").max(200),
  consultedAt: z.string().datetime({ message: "Date de consultation requise" }),
  expiresAt: z.string().optional(),
});

export type EvidenceCreateInput = z.infer<typeof evidenceCreateSchema>;

export const providerCreateSchema = z.object({
  name: z.string().min(1, "Nom requis").max(200),
  sector: z.enum(["STREAMING", "MOBILE_PLAN", "INTERNET_BOX"]),
  officialWebsite: z.string().url("URL invalide").max(500),
  country: z.string().length(2, "Code pays sur 2 caractères"),
});

export type ProviderCreateInput = z.infer<typeof providerCreateSchema>;
