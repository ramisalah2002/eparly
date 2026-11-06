import { describe, expect, it } from "vitest";
import { blockingCount, publicationChecklist, type CheckItem } from "@/lib/checklist";
import { adminEvidence, offer, pricing } from "./fixtures";

const NOW = new Date("2026-11-03T12:00:00Z");

function state(items: CheckItem[], id: string) {
  return items.find((item) => item.id === id)?.state;
}

describe("publicationChecklist", () => {
  it("n'affirme jamais les droits de réutilisation", () => {
    const items = publicationChecklist(offer(), [adminEvidence()], NOW);
    expect(state(items, "reuse-rights")).toBe("unknown");
  });

  it("valide une offre complète dont la preuve couvre les versions courantes", () => {
    const items = publicationChecklist(offer(), [adminEvidence()], NOW);
    expect(blockingCount(items)).toBe(0);
    expect(state(items, "offer-evidence")).toBe("ok");
  });

  it("bloque sans preuve Offre vérifiée", () => {
    const items = publicationChecklist(offer(), [adminEvidence({ verificationStatus: "PENDING" })], NOW);
    expect(state(items, "offer-evidence")).toBe("blocking");
  });

  it("bloque quand le tarif a changé depuis la validation", () => {
    const items = publicationChecklist(offer({ pricing: pricing({ version: 4 }) }), [adminEvidence()], NOW);
    expect(state(items, "offer-evidence")).toBe("blocking");
  });

  it("bloque un tarif inconnu ou incomplet", () => {
    expect(state(publicationChecklist(offer({ pricing: null }), [], NOW), "pricing")).toBe("blocking");
    expect(state(publicationChecklist(offer({ pricing: pricing({ dataUnknown: true }) }), [], NOW), "pricing")).toBe("blocking");
    expect(state(publicationChecklist(offer({ pricing: pricing({ billingPeriod: "UNKNOWN" }) }), [], NOW), "pricing")).toBe(
      "blocking",
    );
    expect(state(publicationChecklist(offer({ pricing: pricing({ amountMinor: null }) }), [], NOW), "pricing")).toBe("blocking");
  });

  it("accepte un tarif à 0 € comme valeur connue", () => {
    expect(state(publicationChecklist(offer({ pricing: pricing({ amountMinor: 0 }) }), [], NOW), "pricing")).toBe("ok");
  });

  it("bloque un fournisseur inactif, une offre future, expirée ou archivée", () => {
    const base = offer();
    expect(state(publicationChecklist({ ...base, provider: { ...base.provider, status: "INACTIVE" } }, [], NOW), "provider")).toBe(
      "blocking",
    );
    expect(state(publicationChecklist(offer({ validFrom: "2026-12-01" }), [], NOW), "validity")).toBe("blocking");
    expect(state(publicationChecklist(offer({ validUntil: "2026-10-01" }), [], NOW), "validity")).toBe("blocking");
    expect(state(publicationChecklist(offer({ publicationStatus: "ARCHIVED" }), [], NOW), "status")).toBe("blocking");
  });

  it("ignore une preuve expirée", () => {
    const items = publicationChecklist(offer(), [adminEvidence({ expiresAt: "2026-11-01T00:00:00Z" })], NOW);
    expect(state(items, "offer-evidence")).toBe("blocking");
  });

  it("exige une preuve Promotion quand une promotion est déclarée", () => {
    const promo = offer({ pricing: pricing({ promotionPriceType: "FIXED_PRICE", priceAfterPromotionMinor: 999, promotionDurationMonths: 6 }) });
    expect(state(publicationChecklist(promo, [adminEvidence()], NOW), "promotion-evidence")).toBe("blocking");
    const withPromoProof = [adminEvidence(), adminEvidence({ id: "p", evidenceScope: "PROMOTION" })];
    expect(state(publicationChecklist(promo, withPromoProof, NOW), "promotion-evidence")).toBe("unknown");
  });

  it("reste « à confirmer » avec une preuve résumée sans versions", () => {
    const { verifiedByUserId, verifiedPricingVersion, verifiedProviderVersion, version, ...summary } = adminEvidence();
    void verifiedByUserId;
    void verifiedPricingVersion;
    void verifiedProviderVersion;
    void version;
    expect(state(publicationChecklist(offer(), [summary], NOW), "offer-evidence")).toBe("unknown");
  });
});
