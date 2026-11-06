import { describe, expect, it } from "vitest";
import { parseCharacteristics } from "@/lib/characteristics";
import { evidenceFlags, reviewQueue } from "@/lib/review-queue";
import { adminEvidence, offer } from "./fixtures";

describe("parseCharacteristics", () => {
  it("marque la valeur sentinelle dataGb=9999 comme incohérente", () => {
    const view = parseCharacteristics('{"dataGb":9999,"calls":"illimités"}');
    const data = view.entries.find((e) => e.key === "dataGb");
    expect(data?.value).toBe("Donnée incohérente");
    expect(data?.value).not.toContain("9999");
    expect(data?.warning).toMatch(/sentinelle/);
  });

  it("demande la revalidation des caractéristiques réseau", () => {
    const view = parseCharacteristics('{"network5G":false}');
    expect(view.entries[0]).toMatchObject({ value: "Non", warning: expect.stringMatching(/revalider/) });
  });

  it("rejette un JSON invalide ou non objet", () => {
    expect(parseCharacteristics("{oops").parseError).toBe(true);
    expect(parseCharacteristics("[1,2]").parseError).toBe(true);
    expect(parseCharacteristics(null)).toEqual({ entries: [], parseError: false });
  });

  it("n'altère pas une clé sans rapport contenant « go »", () => {
    expect(parseCharacteristics('{"category":10000}').entries[0].warning).toBeNull();
  });
});

describe("reviewQueue", () => {
  it("ne liste que les preuves PENDING des offres non archivées, preuve Offre en premier", () => {
    const pendingPricing = adminEvidence({ id: "a", verificationStatus: "PENDING", evidenceScope: "PRICING", createdAt: "2026-10-01T00:00:00Z" });
    const pendingOffer = adminEvidence({ id: "b", verificationStatus: "PENDING", evidenceScope: "OFFER", createdAt: "2026-10-05T00:00:00Z" });
    const queue = reviewQueue([
      offer({ evidence: [pendingPricing, pendingOffer, adminEvidence({ id: "c" })] }),
      offer({ id: "archived", publicationStatus: "ARCHIVED", evidence: [adminEvidence({ id: "d", verificationStatus: "PENDING" })] }),
    ]);
    expect(queue.map((item) => item.evidence.id)).toEqual(["b", "a"]);
  });

  it("signale page d'accueil et date de consultation inconnue", () => {
    const flags = evidenceFlags(adminEvidence({ officialUrl: "https://provider.example.test/", consultedAt: null }));
    expect(flags).toContain("Page d'accueil : revue approfondie");
    expect(flags).toContain("Date de consultation inconnue");
  });
});
