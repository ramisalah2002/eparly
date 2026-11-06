import type { CommercialOffer, EvidenceScope, OfferEvidence } from "./types";

export interface ReviewItem {
  offer: CommercialOffer;
  evidence: OfferEvidence;
  flags: string[];
}

// La preuve « Offre » conditionne la publication : elle passe avant promotion et tarif.
const SCOPE_PRIORITY: Record<EvidenceScope, number> = { OFFER: 0, PROMOTION: 1, PRICING: 2 };

/** Signale les cas où une validation par défaut serait risquée (cahier des charges §5.5). */
export function evidenceFlags(evidence: OfferEvidence): string[] {
  const flags: string[] = [];
  if (!evidence.consultedAt) flags.push("Date de consultation inconnue");
  if (!evidence.officialUrl) {
    flags.push("Aucune source");
  } else {
    try {
      const url = new URL(evidence.officialUrl);
      if (url.pathname === "/" || url.pathname === "") flags.push("Page d'accueil : revue approfondie");
      if (url.protocol !== "https:") flags.push("Source non HTTPS");
    } catch {
      flags.push("URL invalide");
    }
  }
  if (evidence.expiresAt && new Date(evidence.expiresAt) <= new Date()) flags.push("Preuve expirée");
  return flags;
}

export function reviewQueue(offers: CommercialOffer[]): ReviewItem[] {
  const items: ReviewItem[] = [];
  for (const offer of offers) {
    if (offer.publicationStatus === "ARCHIVED") continue;
    for (const evidence of offer.evidence ?? []) {
      if (evidence.verificationStatus === "PENDING") items.push({ offer, evidence, flags: evidenceFlags(evidence) });
    }
  }
  return items.sort((a, b) => {
    const scope =
      SCOPE_PRIORITY[a.evidence.evidenceScope ?? "PRICING"] - SCOPE_PRIORITY[b.evidence.evidenceScope ?? "PRICING"];
    if (scope !== 0) return scope;
    // Les plus anciennes d'abord.
    return a.evidence.createdAt.localeCompare(b.evidence.createdAt);
  });
}
