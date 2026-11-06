import { describe, expect, it } from "vitest";
import { describePricing, shortPrice } from "@/lib/pricing";
import { pricing } from "./fixtures";

describe("describePricing", () => {
  it("affiche un tarif standard sans promotion", () => {
    const view = describePricing(pricing());
    expect(view.standard).toBe("19,99 € par mois");
    expect(view.promotion).toBeNull();
    expect(view.taxes).toBe("TTC");
  });

  it("FIXED_PRICE : priceAfterPromotionMinor est le prix temporaire, amountMinor le tarif ensuite", () => {
    // Cas « Série Free » : 12,99 €/mois pendant 12 mois, puis tarif standard.
    const view = describePricing(
      pricing({
        amountMinor: 1999,
        promotionPriceType: "FIXED_PRICE",
        priceAfterPromotionMinor: 1299,
        promotionDurationMonths: 12,
      }),
    );
    expect(view.promotion?.temporary).toBe("12,99 € par mois");
    expect(view.promotion?.duration).toBe("pendant 12 mois");
    expect(view.standard).toBe("19,99 € par mois");
    expect(view.promotion?.inconsistent).toBeNull();
  });

  it("signale un prix promotionnel non inférieur au tarif standard", () => {
    const view = describePricing(
      pricing({ amountMinor: 999, promotionPriceType: "FIXED_PRICE", priceAfterPromotionMinor: 1299, promotionDurationMonths: 6 }),
    );
    expect(view.promotion?.inconsistent).toMatch(/pas inférieur/);
  });

  it("PERCENTAGE : affiche la remise sans calculer de prix", () => {
    const view = describePricing(pricing({ promotionPriceType: "PERCENTAGE", promotionPercent: 50, promotionDurationMonths: 1 }));
    expect(view.promotion?.temporary).toBe("−50 %");
    expect(view.promotion?.duration).toBe("pendant 1 mois");
  });

  it("ne transforme jamais un frais inconnu en gratuité", () => {
    expect(describePricing(pricing({ activationFeeMinor: null })).activationFee).toBe("Donnée inconnue");
    expect(describePricing(pricing({ activationFeeMinor: 0 })).activationFee).toBe("0,00 €");
    expect(describePricing(pricing({ otherFees: null })).otherFees).toBe("Donnée inconnue");
  });

  it("traite une durée et des taxes absentes comme inconnues", () => {
    const view = describePricing(pricing({ taxesIncluded: null, promotionPriceType: "FIXED_PRICE", priceAfterPromotionMinor: 999 }));
    expect(view.taxes).toBe("Donnée inconnue");
    expect(view.promotion?.duration).toBe("durée inconnue");
  });

  it("considère dataUnknown absent comme non fiable", () => {
    expect(describePricing(pricing({ dataUnknown: null })).dataUnknown).toBe(true);
    expect(describePricing(pricing({ dataUnknown: false })).dataUnknown).toBe(false);
  });
});

describe("shortPrice", () => {
  it("résume la promotion puis le tarif standard", () => {
    expect(
      shortPrice(pricing({ promotionPriceType: "FIXED_PRICE", priceAfterPromotionMinor: 1299, promotionDurationMonths: 12 })),
    ).toBe("12,99 € par mois pendant 12 mois, puis 19,99 € par mois");
    expect(shortPrice(null)).toBe("Aucun tarif");
  });
});
