import { describe, expect, it } from "vitest";
import { monthlyActivity, niceCeiling, recentByDay, sectorProgress, statusDistribution } from "@/lib/dashboard-data";
import { adminEvidence, offer } from "./fixtures";

const NOW = new Date("2026-11-03T12:00:00Z");

describe("monthlyActivity", () => {
  it("couvre les 6 derniers mois, mois courant en dernier, sans valeur inventée", () => {
    const months = monthlyActivity([], NOW);
    expect(months.map((m) => m.key)).toEqual(["2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"]);
    expect(months.every((m) => m.offersAdded === 0 && m.evidenceVerified === 0)).toBe(true);
    expect(months[5].label).toBe("nov");
  });

  it("compte les offres par date de création et les preuves par date de vérification", () => {
    const months = monthlyActivity(
      [
        offer({ createdAt: "2026-11-02T10:00:00Z", evidence: [adminEvidence({ verifiedAt: "2026-10-20T09:05:00Z" })] }),
        offer({ id: "b", createdAt: "2026-11-01T10:00:00Z", evidence: [adminEvidence({ verificationStatus: "PENDING", verifiedAt: null })] }),
        offer({ id: "old", createdAt: "2025-01-01T10:00:00Z" }),
      ],
      NOW,
    );
    expect(months.find((m) => m.key === "2026-11")?.offersAdded).toBe(2);
    expect(months.find((m) => m.key === "2026-10")?.evidenceVerified).toBe(1);
    expect(months.reduce((s, m) => s + m.offersAdded, 0)).toBe(2);
  });

  it("range un événement selon le fuseau de Paris", () => {
    // 31 octobre 23:30 UTC = 1er novembre 00:30 à Paris.
    const months = monthlyActivity([offer({ createdAt: "2026-10-31T23:30:00Z" })], NOW);
    expect(months.find((m) => m.key === "2026-11")?.offersAdded).toBe(1);
  });
});

describe("sectorProgress", () => {
  it("calcule la part d'offres avec preuve Offre vérifiée et l'état associé", () => {
    const progress = sectorProgress([
      offer({ sector: "MOBILE_PLAN", evidence: [adminEvidence()] }),
      offer({ id: "b", sector: "MOBILE_PLAN", evidence: [adminEvidence({ verificationStatus: "PENDING" })] }),
      offer({ id: "c", sector: "STREAMING", evidence: [] }),
      offer({ id: "d", sector: "STREAMING", publicationStatus: "ARCHIVED" }),
    ]);
    const mobile = progress.find((p) => p.sector === "MOBILE_PLAN");
    expect(mobile).toMatchObject({ total: 2, withVerifiedProof: 1, pendingEvidence: 1, percent: 50, state: "in-review" });
    expect(progress.find((p) => p.sector === "STREAMING")).toMatchObject({ total: 1, percent: 0, state: "missing" });
  });

  it("ne renvoie que les secteurs ayant des offres actives, dans l'ordre de l'API", () => {
    const progress = sectorProgress([
      offer({ id: "a", sector: "GAS" }),
      offer({ id: "b", sector: "STREAMING" }),
      offer({ id: "c", sector: "INSURANCE", publicationStatus: "ARCHIVED" }),
    ]);
    expect(progress.map((p) => p.sector)).toEqual(["STREAMING", "GAS"]);
    expect(sectorProgress([])).toEqual([]);
  });

  it("ne compte pas une preuve Tarif comme preuve d'offre", () => {
    const [mobile] = sectorProgress([offer({ evidence: [adminEvidence({ evidenceScope: "PRICING" })] })]);
    expect(mobile.withVerifiedProof).toBe(0);
  });
});

describe("statusDistribution et recentByDay", () => {
  it("compte tous les statuts, y compris à zéro", () => {
    const dist = statusDistribution([offer(), offer({ id: "v", publicationStatus: "VERIFIED" })]);
    expect(dist).toHaveLength(5);
    expect(dist.find((d) => d.status === "DRAFT")?.count).toBe(1);
    expect(dist.find((d) => d.status === "ARCHIVED")?.count).toBe(0);
  });

  it("regroupe par jour avec Aujourd'hui et Hier", () => {
    const groups = recentByDay(
      [
        offer({ id: "1", updatedAt: "2026-11-03T08:00:00Z" }),
        offer({ id: "2", updatedAt: "2026-11-02T08:00:00Z" }),
        offer({ id: "3", updatedAt: "2026-10-20T08:00:00Z" }),
      ],
      NOW,
    );
    expect(groups.map((g) => g.label)).toEqual(["Aujourd'hui", "Hier", "Mardi 20 octobre"]);
  });
});

describe("niceCeiling", () => {
  it("arrondit le haut de l'axe à une graduation propre", () => {
    expect(niceCeiling(0)).toBe(1);
    expect(niceCeiling(3)).toBe(5);
    expect(niceCeiling(8)).toBe(10);
    expect(niceCeiling(12)).toBe(20);
    expect(niceCeiling(83)).toBe(100);
  });
});
