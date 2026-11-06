import type { AdminEvidence, CommercialOffer, OfferEvidence, OfferPricing } from "./types";

// Lecture indicative des contrôles de CatalogAdminService.publish et OfferAvailabilityService.
// Le backend reste seul juge : un item « ok » ici ne garantit pas la publication, et
// la fraîcheur des preuves (seuil configuré côté serveur) n'est pas évaluable depuis le front.

export type CheckState = "ok" | "blocking" | "unknown";

export interface CheckItem {
  id: string;
  label: string;
  state: CheckState;
  detail: string;
}

function isReliablePricing(pricing: OfferPricing): boolean {
  return (
    pricing.amountMinor !== null &&
    pricing.amountMinor >= 0 &&
    !!pricing.currency?.trim() &&
    pricing.billingPeriod !== null &&
    pricing.billingPeriod !== "UNKNOWN" &&
    pricing.dataUnknown === false
  );
}

function isAdminEvidence(evidence: OfferEvidence | AdminEvidence): evidence is AdminEvidence {
  return "verifiedPricingVersion" in evidence;
}

function notExpired(evidence: OfferEvidence, now: Date): boolean {
  return !evidence.expiresAt || new Date(evidence.expiresAt) > now;
}

function verifiedOfScope(
  evidences: (OfferEvidence | AdminEvidence)[],
  scope: "OFFER" | "PROMOTION",
  now: Date,
): (OfferEvidence | AdminEvidence)[] {
  return evidences.filter((e) => e.evidenceScope === scope && e.verificationStatus === "VERIFIED" && notExpired(e, now));
}

export function publicationChecklist(
  offer: CommercialOffer,
  evidences: (OfferEvidence | AdminEvidence)[],
  now: Date = new Date(),
): CheckItem[] {
  const items: CheckItem[] = [];
  const pricing = offer.pricing;
  const today = now.toISOString().slice(0, 10);

  items.push({
    id: "status",
    label: "Offre non archivée",
    state: offer.publicationStatus === "ARCHIVED" ? "blocking" : "ok",
    detail: offer.publicationStatus === "ARCHIVED" ? "Une offre archivée ne peut pas être publiée." : "Statut compatible.",
  });

  items.push({
    id: "provider",
    label: "Fournisseur actif",
    state: offer.provider.status === "ACTIVE" ? "ok" : "blocking",
    detail: offer.provider.status === "ACTIVE" ? offer.provider.name : `${offer.provider.name} est inactif.`,
  });

  items.push({
    id: "pricing",
    label: "Tarif complet et fiable",
    state: !pricing ? "blocking" : isReliablePricing(pricing) ? "ok" : "blocking",
    detail: !pricing
      ? "Aucun tarif enregistré."
      : isReliablePricing(pricing)
        ? "Montant, devise et périodicité connus."
        : "Montant, devise, périodicité ou indicateur « donnée inconnue » à compléter.",
  });

  const future = offer.validFrom !== null && offer.validFrom > today;
  const expired = offer.validUntil !== null && offer.validUntil < today;
  items.push({
    id: "validity",
    label: "Période de validité en cours",
    state: future || expired ? "blocking" : "ok",
    detail: future
      ? `Commence le ${offer.validFrom}.`
      : expired
        ? `Terminée le ${offer.validUntil}.`
        : "Aucune borne bloquante.",
  });

  const offerProofs = verifiedOfScope(evidences, "OFFER", now);
  let proofState: CheckState = offerProofs.length === 0 ? "blocking" : "unknown";
  let proofDetail =
    offerProofs.length === 0
      ? "Aucune preuve « Offre » vérifiée et non expirée."
      : "Preuve vérifiée présente ; fraîcheur et versions à confirmer par le backend.";
  const detailed = offerProofs.filter(isAdminEvidence);
  if (detailed.length > 0 && pricing) {
    const matching = detailed.some(
      (e) =>
        e.verifiedByUserId !== null &&
        e.verifiedPricingVersion === pricing.version &&
        e.verifiedProviderVersion === offer.provider.version,
    );
    proofState = matching ? "ok" : "blocking";
    proofDetail = matching
      ? "Preuve validée sur les versions courantes du tarif et du fournisseur (fraîcheur évaluée par le backend)."
      : "Le tarif ou le fournisseur a changé depuis la validation : nouvelle vérification requise.";
  }
  items.push({ id: "offer-evidence", label: "Preuve « Offre » vérifiée", state: proofState, detail: proofDetail });

  if (pricing?.promotionPriceType) {
    const promoProofs = verifiedOfScope(evidences, "PROMOTION", now);
    items.push({
      id: "promotion-evidence",
      label: "Preuve « Promotion » vérifiée",
      state: promoProofs.length > 0 ? "unknown" : "blocking",
      detail:
        promoProofs.length > 0
          ? "Preuve présente ; fraîcheur à confirmer par le backend."
          : "Une promotion est déclarée sans preuve dédiée vérifiée.",
    });
  }

  // Les droits de réutilisation (GET /offers/{id}/reuse-rights) ne sont pas encore lus par la console :
  // l'état ne peut pas être affirmé.
  items.push({
    id: "reuse-rights",
    label: "Droits de réutilisation établis",
    state: "unknown",
    detail: "Pas encore lu par la console : à vérifier avant publication.",
  });

  return items;
}

export function blockingCount(items: CheckItem[]): number {
  return items.filter((item) => item.state === "blocking").length;
}
