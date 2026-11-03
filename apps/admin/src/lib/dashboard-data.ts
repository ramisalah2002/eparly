import type { CommercialOffer, OfferStatus, Sector } from "./types";
import { OFFER_STATUSES, SECTORS } from "./types";

// Agrégats du tableau de bord, calculés uniquement à partir des offres renvoyées par l'API.
// Aucune valeur n'est extrapolée : un mois sans événement vaut 0 événement enregistré.

const TIME_ZONE = "Europe/Paris";

const monthKeyFormatter = new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE, year: "numeric", month: "2-digit" });
const monthLabelFormatter = new Intl.DateTimeFormat("fr-FR", { timeZone: TIME_ZONE, month: "short" });
const monthFullFormatter = new Intl.DateTimeFormat("fr-FR", { timeZone: TIME_ZONE, month: "long", year: "numeric" });
const dayKeyFormatter = new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" });

/** « 2026-11 » dans le fuseau de Paris. */
export function monthKey(date: Date): string {
  return monthKeyFormatter.format(date).slice(0, 7);
}

function dayKey(date: Date): string {
  return dayKeyFormatter.format(date);
}

export interface MonthlyActivity {
  key: string;
  label: string;
  fullLabel: string;
  offersAdded: number;
  evidenceVerified: number;
}

/** Offres ajoutées (createdAt) et preuves vérifiées (verifiedAt) sur les N derniers mois. */
export function monthlyActivity(offers: CommercialOffer[], now: Date, months = 6): MonthlyActivity[] {
  const buckets: MonthlyActivity[] = [];
  const [year, month] = monthKey(now).split("-").map(Number);
  for (let i = months - 1; i >= 0; i -= 1) {
    // Le 15 du mois évite tout basculement de fuseau en début ou fin de mois.
    const ref = new Date(Date.UTC(year, month - 1 - i, 15));
    buckets.push({
      key: monthKey(ref),
      label: monthLabelFormatter.format(ref).replace(".", ""),
      fullLabel: monthFullFormatter.format(ref),
      offersAdded: 0,
      evidenceVerified: 0,
    });
  }
  const index = new Map(buckets.map((b) => [b.key, b]));
  for (const offer of offers) {
    const added = index.get(monthKey(new Date(offer.createdAt)));
    if (added) added.offersAdded += 1;
    for (const evidence of offer.evidence ?? []) {
      if (evidence.verificationStatus !== "VERIFIED" || !evidence.verifiedAt) continue;
      const verified = index.get(monthKey(new Date(evidence.verifiedAt)));
      if (verified) verified.evidenceVerified += 1;
    }
  }
  return buckets;
}

export type SectorState = "empty" | "complete" | "in-review" | "missing";

export interface SectorProgress {
  sector: Sector;
  total: number;
  /** Offres disposant d'au moins une preuve « Offre » vérifiée. */
  withVerifiedProof: number;
  pendingEvidence: number;
  /** Part entière 0–100, ou null s'il n'y a aucune offre. */
  percent: number | null;
  state: SectorState;
}

/** Avancement de la vérification humaine, par secteur (hors offres archivées). */
export function sectorProgress(offers: CommercialOffer[]): SectorProgress[] {
  return SECTORS.map((sector) => {
    const inSector = offers.filter((o) => o.sector === sector && o.publicationStatus !== "ARCHIVED");
    const withVerifiedProof = inSector.filter((o) =>
      (o.evidence ?? []).some((e) => e.evidenceScope === "OFFER" && e.verificationStatus === "VERIFIED"),
    ).length;
    const pendingEvidence = inSector.reduce(
      (sum, o) => sum + (o.evidence ?? []).filter((e) => e.verificationStatus === "PENDING").length,
      0,
    );
    const total = inSector.length;
    const percent = total === 0 ? null : Math.floor((withVerifiedProof / total) * 100);
    const state: SectorState =
      total === 0 ? "empty" : withVerifiedProof === total ? "complete" : pendingEvidence > 0 ? "in-review" : "missing";
    return { sector, total, withVerifiedProof, pendingEvidence, percent, state };
  });
}

export function statusDistribution(offers: CommercialOffer[]): { status: OfferStatus; count: number }[] {
  return OFFER_STATUSES.map((status) => ({ status, count: offers.filter((o) => o.publicationStatus === status).length }));
}

export interface DayGroup {
  key: string;
  label: string;
  offers: CommercialOffer[];
}

const dayLabelFormatter = new Intl.DateTimeFormat("fr-FR", { timeZone: TIME_ZONE, weekday: "long", day: "numeric", month: "long" });

/** Dernières offres modifiées, regroupées par jour (« Aujourd'hui », « Hier », date). */
export function recentByDay(offers: CommercialOffer[], now: Date, limit = 8): DayGroup[] {
  const today = dayKey(now);
  const yesterday = dayKey(new Date(now.getTime() - 86_400_000));
  const recent = [...offers].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, limit);
  const groups: DayGroup[] = [];
  for (const offer of recent) {
    const date = new Date(offer.updatedAt);
    const key = dayKey(date);
    let group = groups.find((g) => g.key === key);
    if (!group) {
      const label = key === today ? "Aujourd'hui" : key === yesterday ? "Hier" : dayLabelFormatter.format(date);
      group = { key, label: label.charAt(0).toUpperCase() + label.slice(1), offers: [] };
      groups.push(group);
    }
    group.offers.push(offer);
  }
  return groups;
}

/** Graduation « propre » de l'axe : 1, 2, 5 × 10^n au-dessus du maximum. */
export function niceCeiling(max: number): number {
  if (max <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(max));
  for (const step of [1, 2, 5, 10]) {
    if (step * magnitude >= max) return step * magnitude;
  }
  return 10 * magnitude;
}
