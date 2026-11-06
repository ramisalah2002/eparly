import { describe, expect, it } from "vitest";
import { describeApiError, formatMinor, UNKNOWN_LABEL } from "@/lib/format";

describe("formatMinor", () => {
  it("formate des centimes sans arithmétique flottante", () => {
    expect(formatMinor(1299, "EUR")).toBe("12,99 €");
    expect(formatMinor(5, "EUR")).toBe("0,05 €");
    expect(formatMinor(123456789, "EUR")).toBe("1 234 567,89 €");
  });

  it("distingue 0 connu d'un montant inconnu", () => {
    expect(formatMinor(0, "EUR")).toBe("0,00 €");
    expect(formatMinor(null, "EUR")).toBe(UNKNOWN_LABEL);
    expect(formatMinor(undefined, "EUR")).toBe(UNKNOWN_LABEL);
  });

  it("signale une devise absente plutôt que d'en supposer une", () => {
    expect(formatMinor(1000, null)).toBe("10,00 (devise inconnue)");
  });

  it("gère les montants négatifs et les autres devises", () => {
    expect(formatMinor(-250, "EUR")).toBe("-2,50 €");
    expect(formatMinor(999, "USD")).toBe("9,99 USD");
  });
});

describe("describeApiError", () => {
  it("traduit les refus de publication connus", () => {
    expect(describeApiError(400, "Publication requires pricing")).toMatch(/pas de tarif/);
    expect(describeApiError(400, "Offer, provider, or pricing changed after the evidence was validated")).toMatch(
      /a changé depuis la validation/,
    );
  });

  it("donne un message par classe d'erreur", () => {
    expect(describeApiError(0, null)).toMatch(/injoignable/);
    expect(describeApiError(403, "Forbidden")).toMatch(/Accès refusé/);
    expect(describeApiError(503, null)).toMatch(/serveur/);
  });

  it("conserve un message de validation inconnu", () => {
    expect(describeApiError(400, "Summary must not be blank")).toBe("Summary must not be blank");
  });
});
