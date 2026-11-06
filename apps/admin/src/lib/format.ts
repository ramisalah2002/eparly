import type {
  BillingPeriod,
  EvidenceScope,
  EvidenceStatus,
  OfferStatus,
  ProviderStatus,
  Sector,
} from "./types";

export const UNKNOWN_LABEL = "Donnée inconnue";

/** Montant en unités mineures → texte. `null` reste inconnu : jamais converti en 0. */
export function formatMinor(amountMinor: number | null | undefined, currency: string | null | undefined): string {
  if (amountMinor === null || amountMinor === undefined || !Number.isFinite(amountMinor)) return UNKNOWN_LABEL;
  const sign = amountMinor < 0 ? "-" : "";
  const abs = Math.abs(Math.trunc(amountMinor));
  // Arithmétique entière : pas de passage par une division flottante des centimes.
  const units = Math.floor(abs / 100);
  const cents = String(abs % 100).padStart(2, "0");
  const symbol = !currency ? "(devise inconnue)" : currency === "EUR" ? "€" : currency;
  return `${sign}${formatInteger(units)},${cents} ${symbol}`;
}

function formatInteger(value: number): string {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

const dateFormatter = new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeZone: "Europe/Paris" });
const dateTimeFormatter = new Intl.DateTimeFormat("fr-FR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Europe/Paris",
});

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateTimeFormatter.format(date);
}

export const SECTOR_LABELS: Record<Sector, string> = {
  STREAMING: "Streaming vidéo",
  MOBILE_PLAN: "Forfait mobile",
  INTERNET_BOX: "Box internet",
  MUSIC_STREAMING: "Streaming musical",
  CLOUD_STORAGE: "Stockage cloud",
  SOFTWARE_SAAS: "Logiciel / SaaS",
  GAMING: "Jeux vidéo",
  ELECTRICITY: "Électricité",
  GAS: "Gaz",
  INSURANCE: "Assurance",
  BANKING: "Banque",
  SPORT_FITNESS: "Sport & fitness",
  PRESS: "Presse",
  TRANSPORT: "Transport",
  OTHER_RECURRING: "Autre abonnement",
};

export const OFFER_STATUS_LABELS: Record<OfferStatus, string> = {
  DRAFT: "Brouillon",
  VERIFIED: "Publiée",
  STALE: "Périmée",
  EXPIRED: "Expirée",
  ARCHIVED: "Archivée",
};

export const EVIDENCE_STATUS_LABELS: Record<EvidenceStatus, string> = {
  PENDING: "À vérifier",
  VERIFIED: "Vérifiée",
  EXPIRED: "Expirée",
  INVALID: "Invalidée",
};

export const EVIDENCE_SCOPE_LABELS: Record<EvidenceScope, string> = {
  OFFER: "Offre",
  PRICING: "Tarif",
  PROMOTION: "Promotion",
};

export const PROVIDER_STATUS_LABELS: Record<ProviderStatus, string> = {
  ACTIVE: "Actif",
  INACTIVE: "Inactif",
};

export const BILLING_LABELS: Record<BillingPeriod, string> = {
  WEEKLY: "par semaine",
  MONTHLY: "par mois",
  QUARTERLY: "par trimestre",
  YEARLY: "par an",
  UNKNOWN: "périodicité inconnue",
};

/** Traduction des refus métier connus de CatalogAdminService ; sinon le message serveur brut. */
const BACKEND_MESSAGES: Record<string, string> = {
  "Publication requires pricing": "Publication refusée : l'offre n'a pas de tarif.",
  "Publication requires reliable recurring pricing":
    "Publication refusée : tarif incomplet (montant, devise, périodicité ou donnée inconnue).",
  "Future offers remain drafts until their start date":
    "Publication refusée : l'offre ne commence pas encore (date de début future).",
  "Publication requires fresh, verified offer evidence":
    "Publication refusée : aucune preuve « Offre » vérifiée, fraîche et non expirée.",
  "Publication requires evidence scoped to the offer":
    "Publication refusée : la preuve courante ne porte pas sur l'offre.",
  "Offer, provider, or pricing changed after the evidence was validated":
    "Publication refusée : l'offre, le fournisseur ou le tarif a changé depuis la validation de la preuve.",
  "Archived offers cannot be published": "Publication refusée : une offre archivée ne peut pas être publiée.",
  "Offer does not satisfy current availability policy":
    "Publication refusée : l'offre ne respecte pas la politique de disponibilité.",
  "Evidence has changed; reload it":
    "La preuve a été modifiée entre-temps. Rechargez la page avant de décider.",
  "The resource changed; reload it and retry":
    "La ressource a été modifiée entre-temps. Rechargez la page puis recommencez.",
  "Request validation failed": "Requête invalide : vérifiez les champs saisis.",
  "Provider already exists or sector is invalid": "Un fournisseur portant ce nom existe déjà dans ce secteur.",
  "Provider already exists for this sector": "Un fournisseur portant ce nom existe déjà dans ce secteur.",
  "Provider sector cannot change while offers exist":
    "Le secteur ne peut pas changer tant que des offres sont rattachées à ce fournisseur.",
  "Provider has changed; reload it":
    "Le fournisseur a été modifié entre-temps. Rechargez la page avant de recommencer.",
  "Official website must be a valid HTTPS URL": "Le site officiel doit être une URL HTTPS valide.",
  "Provider name cannot be blank": "Le nom du fournisseur est obligatoire.",
  "Provider not found": "Fournisseur introuvable.",
};

export function describeApiError(status: number, message: string | null | undefined): string {
  if (message && BACKEND_MESSAGES[message]) return BACKEND_MESSAGES[message];
  switch (status) {
    case 0:
      return "API Eparly injoignable. Vérifiez que le backend est démarré.";
    case 401:
      return "Session expirée. Reconnectez-vous.";
    case 403:
      return "Accès refusé : ce compte ne dispose pas de la permission requise.";
    case 404:
      return "Ressource introuvable.";
    case 409:
      return message ?? "Conflit : la ressource a changé. Rechargez la page.";
    case 429:
      return "Trop de requêtes. Patientez avant de réessayer.";
    default:
      if (status >= 500) return "Erreur du serveur Eparly. Réessayez plus tard.";
      return message ?? `Erreur ${status}.`;
  }
}
