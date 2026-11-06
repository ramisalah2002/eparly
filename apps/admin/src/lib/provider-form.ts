import type { OfferProvider, ProviderStatus, Sector } from "./types";

// Règles du formulaire fournisseur, alignées sur ProviderCreateRequest / ProviderPatchRequest.
// Elles évitent un aller-retour inutile ; Spring Boot reste seul juge de la validité.

export interface ProviderDraft {
  name: string;
  sector: Sector;
  officialWebsite: string;
  country: string;
}

export type ProviderErrors = Partial<Record<keyof ProviderDraft, string>>;

export function draftFrom(provider: OfferProvider | null): ProviderDraft {
  return {
    name: provider?.name ?? "",
    sector: provider?.sector ?? "STREAMING",
    officialWebsite: provider?.officialWebsite ?? "",
    country: provider?.country ?? "FR",
  };
}

export function validateProvider(draft: ProviderDraft): ProviderErrors {
  const errors: ProviderErrors = {};
  const name = draft.name.trim();
  if (!name) errors.name = "Le nom est obligatoire.";
  else if (name.length > 200) errors.name = "200 caractères maximum.";

  const website = draft.officialWebsite.trim();
  if (!website) errors.officialWebsite = "Le site officiel est obligatoire.";
  else if (website.length > 500) errors.officialWebsite = "500 caractères maximum.";
  else if (!isHttpsUrl(website)) errors.officialWebsite = "Saisissez une URL HTTPS complète (https://…).";

  if (!/^[a-z]{2}$/i.test(draft.country.trim())) errors.country = "Code pays à 2 lettres (ex. FR).";
  return errors;
}

function isHttpsUrl(value: string): boolean {
  if (/\s/.test(value)) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname.length > 0;
  } catch {
    return false;
  }
}

function normalized(draft: ProviderDraft): ProviderDraft {
  return {
    name: draft.name.trim(),
    sector: draft.sector,
    officialWebsite: draft.officialWebsite.trim(),
    country: draft.country.trim().toUpperCase(),
  };
}

export function providerCreateBody(draft: ProviderDraft): ProviderDraft {
  return normalized(draft);
}

export interface ProviderPatchBody {
  expectedVersion: number;
  name?: string;
  sector?: Sector;
  officialWebsite?: string;
  country?: string;
  status?: ProviderStatus;
}

/** Corps PATCH ne contenant que les champs modifiés, ou null si rien ne change. */
export function providerPatchBody(provider: OfferProvider, draft: ProviderDraft): ProviderPatchBody | null {
  const next = normalized(draft);
  const body: ProviderPatchBody = { expectedVersion: provider.version };
  if (next.name !== provider.name) body.name = next.name;
  if (next.sector !== provider.sector) body.sector = next.sector;
  if (next.officialWebsite !== (provider.officialWebsite ?? "")) body.officialWebsite = next.officialWebsite;
  if (next.country !== (provider.country ?? "")) body.country = next.country;
  return Object.keys(body).length > 1 ? body : null;
}
