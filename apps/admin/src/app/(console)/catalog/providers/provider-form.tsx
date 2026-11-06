"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent, type ReactNode } from "react";
import { buttonClass } from "@/components/button-class";
import { bffMutation } from "@/lib/client/bff";
import { SECTOR_LABELS } from "@/lib/format";
import {
  draftFrom,
  providerCreateBody,
  providerPatchBody,
  validateProvider,
  type ProviderDraft,
  type ProviderErrors,
} from "@/lib/provider-form";
import { SECTORS, type OfferProvider, type Sector } from "@/lib/types";

const FIELD = "mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-foreground";

/**
 * Création (provider null) ou modification d'un fournisseur. Rien n'est supposé enregistré
 * avant la réponse du serveur ; en modification, seuls les champs changés sont envoyés.
 */
export function ProviderForm({
  provider,
  offerCount,
  publishedCount,
}: {
  provider: OfferProvider | null;
  /** Offres rattachées : le backend interdit alors de changer de secteur. */
  offerCount: number | null;
  /** Offres publiées : elles repasseront « Périmée » après modification. */
  publishedCount: number | null;
}) {
  const router = useRouter();
  const ids = useId();
  const [draft, setDraft] = useState<ProviderDraft>(() => draftFrom(provider));
  const [errors, setErrors] = useState<ProviderErrors>({});
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const sectorLocked = provider !== null && offerCount !== 0;

  function update<K extends keyof ProviderDraft>(key: K, value: ProviderDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const found = validateProvider(draft);
    setErrors(found);
    setFeedback(null);
    if (Object.keys(found).length > 0) return;

    if (provider) {
      const body = providerPatchBody(provider, draft);
      if (!body) {
        setFeedback("Aucune modification à enregistrer.");
        return;
      }
      setBusy(true);
      const result = await bffMutation<OfferProvider>(`/api/admin/catalog/providers/${provider.id}`, "PATCH", body);
      setBusy(false);
      if (!result.ok) return setFeedback(result.message);
      router.push(`/catalog/providers/${provider.id}`);
      router.refresh();
      return;
    }

    setBusy(true);
    const result = await bffMutation<OfferProvider>("/api/admin/catalog/providers", "POST", providerCreateBody(draft));
    setBusy(false);
    if (!result.ok) return setFeedback(result.message);
    router.push(result.data ? `/catalog/providers/${result.data.id}` : "/catalog/providers");
    router.refresh();
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <Field id={`${ids}-name`} label="Nom" error={errors.name}>
        <input
          id={`${ids}-name`}
          value={draft.name}
          onChange={(e) => update("name", e.target.value)}
          maxLength={200}
          autoComplete="off"
          aria-invalid={!!errors.name}
          className={FIELD}
        />
      </Field>

      <Field
        id={`${ids}-sector`}
        label="Secteur"
        hint={sectorLocked ? "Non modifiable : des offres sont rattachées à ce fournisseur." : undefined}
      >
        <select
          id={`${ids}-sector`}
          value={draft.sector}
          onChange={(e) => update("sector", e.target.value as Sector)}
          disabled={sectorLocked}
          className={FIELD}
        >
          {SECTORS.map((s) => (
            <option key={s} value={s}>
              {SECTOR_LABELS[s]}
            </option>
          ))}
        </select>
      </Field>

      <Field id={`${ids}-website`} label="Site officiel" hint="URL HTTPS complète." error={errors.officialWebsite}>
        <input
          id={`${ids}-website`}
          type="url"
          inputMode="url"
          value={draft.officialWebsite}
          onChange={(e) => update("officialWebsite", e.target.value)}
          placeholder="https://"
          maxLength={500}
          aria-invalid={!!errors.officialWebsite}
          className={FIELD}
        />
      </Field>

      <Field id={`${ids}-country`} label="Pays" hint="Code ISO à 2 lettres." error={errors.country}>
        <input
          id={`${ids}-country`}
          value={draft.country}
          onChange={(e) => update("country", e.target.value)}
          maxLength={2}
          autoComplete="off"
          aria-invalid={!!errors.country}
          className={`${FIELD} max-w-24 uppercase`}
        />
      </Field>

      {provider && !!publishedCount && (
        <p className="rounded-xl bg-warn-soft px-3 py-2 text-warn">
          {publishedCount} offre{publishedCount > 1 ? "s" : ""} publiée{publishedCount > 1 ? "s" : ""} repasser
          {publishedCount > 1 ? "ont" : "a"} « Périmée » après enregistrement : leurs preuves devront être revalidées.
        </p>
      )}

      {feedback && (
        <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-danger">
          {feedback}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        <button type="submit" className={buttonClass("primary")} disabled={busy}>
          {busy ? "Enregistrement…" : provider ? "Enregistrer les modifications" : "Créer le fournisseur"}
        </button>
        <Link
          href={provider ? `/catalog/providers/${provider.id}` : "/catalog/providers"}
          className={buttonClass("secondary")}
        >
          Annuler
        </Link>
      </div>
    </form>
  );
}

function Field({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="block font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p className="mt-1 text-xs text-danger">{error}</p>
      ) : (
        hint && <p className="mt-1 text-xs text-muted">{hint}</p>
      )}
    </div>
  );
}
