import { BILLING_LABELS, formatMinor, UNKNOWN_LABEL } from "./format";
import type { OfferPricing } from "./types";

// Sémantique backend (catalog-admin-api.md, « Prix et promotions ») :
// - amountMinor = tarif standard, c'est-à-dire le prix APRÈS la promotion ;
// - FIXED_PRICE : priceAfterPromotionMinor porte, malgré son nom historique, le montant
//   temporaire pendant la promotion ;
// - PERCENTAGE : promotionPercent s'applique pendant promotionDurationMonths.
// Ne pas déduire le sens de ces champs de leur nom.

export interface PromotionView {
  kind: "FIXED_PRICE" | "PERCENTAGE";
  /** Texte du tarif temporaire, ex. « 12,99 € par mois » ou « −50 % ». */
  temporary: string;
  duration: string;
  window: string | null;
  verified: boolean | null;
  inconsistent: string | null;
}

export interface PricingView {
  standard: string;
  billing: string;
  taxes: string;
  activationFee: string;
  otherFees: string;
  discountConditions: string | null;
  promotion: PromotionView | null;
  dataUnknown: boolean;
}

function months(value: number | null): string {
  if (value === null || value === undefined) return "durée inconnue";
  return value === 1 ? "pendant 1 mois" : `pendant ${value} mois`;
}

export function describePricing(pricing: OfferPricing): PricingView {
  const billing = pricing.billingPeriod ? BILLING_LABELS[pricing.billingPeriod] : BILLING_LABELS.UNKNOWN;
  let promotion: PromotionView | null = null;

  if (pricing.promotionPriceType === "FIXED_PRICE") {
    const temporary = pricing.priceAfterPromotionMinor;
    promotion = {
      kind: "FIXED_PRICE",
      temporary: `${formatMinor(temporary, pricing.currency)} ${billing}`,
      duration: months(pricing.promotionDurationMonths),
      window: promotionWindow(pricing),
      verified: pricing.promotionVerified,
      inconsistent:
        temporary !== null && pricing.amountMinor !== null && temporary >= pricing.amountMinor
          ? "Le prix promotionnel n'est pas inférieur au tarif standard : à vérifier."
          : null,
    };
  } else if (pricing.promotionPriceType === "PERCENTAGE") {
    const percent = pricing.promotionPercent;
    promotion = {
      kind: "PERCENTAGE",
      temporary: percent === null ? `Remise ${UNKNOWN_LABEL.toLowerCase()}` : `−${percent} %`,
      duration: months(pricing.promotionDurationMonths),
      window: promotionWindow(pricing),
      verified: pricing.promotionVerified,
      inconsistent: percent !== null && (percent <= 0 || percent >= 100) ? "Pourcentage de remise hors limites." : null,
    };
  }

  return {
    standard: `${formatMinor(pricing.amountMinor, pricing.currency)} ${billing}`,
    billing,
    taxes: pricing.taxesIncluded === null ? UNKNOWN_LABEL : pricing.taxesIncluded ? "TTC" : "HT",
    // 0 est une valeur connue (« 0,00 € »), distincte d'un frais non renseigné.
    activationFee: formatMinor(pricing.activationFeeMinor, pricing.currency),
    otherFees: pricing.otherFees?.trim() ? pricing.otherFees : UNKNOWN_LABEL,
    discountConditions: pricing.discountConditions?.trim() ? pricing.discountConditions : null,
    promotion,
    dataUnknown: pricing.dataUnknown !== false,
  };
}

function promotionWindow(pricing: OfferPricing): string | null {
  if (!pricing.promotionValidFrom && !pricing.promotionValidUntil) return null;
  return `Souscription du ${pricing.promotionValidFrom ?? "?"} au ${pricing.promotionValidUntil ?? "?"}`;
}

/** Résumé court pour la table des offres. */
export function shortPrice(pricing: OfferPricing | null): string {
  if (!pricing) return "Aucun tarif";
  const view = describePricing(pricing);
  return view.promotion
    ? `${view.promotion.temporary} ${view.promotion.duration}, puis ${view.standard}`
    : view.standard;
}
