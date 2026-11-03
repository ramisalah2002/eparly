"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { AlertCircle, ArrowLeft, LoaderCircle, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { HttpError, apiRequest } from "@/lib/api";
import type { OfferProviderResponse, OfferSector } from "@/types/offer";

const providerFormSchema = z.object({
  name: z.string().trim().min(1, "Le nom est requis.").max(200, "200 caractères maximum."),
  sector: z.enum(["STREAMING", "MOBILE_PLAN", "INTERNET_BOX"]),
  officialWebsite: z
    .string()
    .trim()
    .url("Saisissez une URL valide.")
    .max(500, "500 caractères maximum.")
    .refine((value) => {
      try {
        const url = new URL(value);
        return url.protocol === "https:" && Boolean(url.hostname);
      } catch {
        return false;
      }
    }, "Le site officiel doit utiliser une URL HTTPS."),
  country: z.string().trim().regex(/^[a-zA-Z]{2}$/, "Saisissez un code pays à 2 lettres."),
});

type ProviderFormValues = z.infer<typeof providerFormSchema>;

const sectorLabels: Record<OfferSector, string> = {
  STREAMING: "Streaming",
  MOBILE_PLAN: "Forfait mobile",
  INTERNET_BOX: "Internet / box",
};

interface ProviderFormProps {
  provider?: OfferProviderResponse;
}

export function ProviderForm({ provider }: ProviderFormProps) {
  const router = useRouter();
  const [serverError, setServerError] = useState<{ status?: number; message: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const isEditing = Boolean(provider);

  const form = useForm<ProviderFormValues>({
    resolver: zodResolver(providerFormSchema),
    defaultValues: {
      name: provider?.name ?? "",
      sector: provider?.sector ?? "STREAMING",
      officialWebsite: provider?.officialWebsite ?? "",
      country: provider?.country ?? "FR",
    },
  });

  const onSubmit = async (values: ProviderFormValues) => {
    setServerError(null);
    setSubmitting(true);
    try {
      if (provider) {
        await apiRequest<OfferProviderResponse>(`/api/admin/catalog/providers/${provider.id}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            expectedVersion: provider.version,
            name: values.name,
            sector: values.sector,
            officialWebsite: values.officialWebsite,
            country: values.country.toUpperCase(),
          }),
        });
        router.replace(`/catalog/providers/${provider.id}?updated=1`);
      } else {
        const created = await apiRequest<OfferProviderResponse>("/api/admin/catalog/providers", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name: values.name,
            sector: values.sector,
            officialWebsite: values.officialWebsite,
            country: values.country.toUpperCase(),
          }),
        });
        router.replace(`/catalog/providers?created=${encodeURIComponent(created.id)}`);
      }
    } catch (error) {
      const requestError = error instanceof HttpError ? error : null;
      let message = error instanceof Error ? error.message : "La sauvegarde a échoué.";
      if (requestError?.status === 409) {
        message = requestError.message.toLowerCase().includes("sector cannot change")
          ? "Le secteur ne peut pas être modifié tant que des offres sont associées à ce fournisseur."
          : isEditing && requestError.message.toLowerCase().includes("changed")
            ? "Le fournisseur a été modifié depuis son chargement. Actualisez ses données avant de réessayer."
            : "Un fournisseur portant déjà ce nom existe pour ce secteur.";
      } else if (requestError?.status === 400 || requestError?.status === 422) {
        message = `Les données ont été refusées par l’API : ${requestError.message}`;
      } else if (requestError?.status === 401) {
        message = "Votre session a expiré. Reconnectez-vous pour continuer.";
      } else if (requestError?.status === 403) {
        message = "Votre compte ne dispose pas de la permission CATALOG_ADMIN.";
      } else if (requestError?.status === 500) {
        message = "Le serveur a rencontré une erreur. Aucune confirmation d’enregistrement n’a été reçue.";
      }
      setServerError({ status: requestError?.status, message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={form.handleSubmit(onSubmit)}
      className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm"
    >
      <div className="space-y-6 p-5 sm:p-8">
        {serverError && (
          <div role="alert" className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <div>
              <p className="font-semibold">
                {serverError.status ? `Erreur ${serverError.status}` : "Échec de l’enregistrement"}
              </p>
              <p className="mt-1">{serverError.message}</p>
            </div>
          </div>
        )}

        <div className="grid gap-5 md:grid-cols-2">
          <div className="space-y-2 md:col-span-2">
            <label htmlFor="provider-name" className="text-sm font-medium text-zinc-800">
              Nom du fournisseur
            </label>
            <input
              id="provider-name"
              autoComplete="organization"
              maxLength={200}
              {...form.register("name")}
              className="h-11 w-full rounded-lg border border-zinc-300 px-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="Ex. Fournisseur"
            />
            {form.formState.errors.name && <p className="text-xs text-red-600">{form.formState.errors.name.message}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="provider-sector" className="text-sm font-medium text-zinc-800">
              Secteur
            </label>
            <select
              id="provider-sector"
              {...form.register("sector")}
              className="h-11 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            >
              {Object.entries(sectorLabels).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
            {form.formState.errors.sector && <p className="text-xs text-red-600">{form.formState.errors.sector.message}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="provider-country" className="text-sm font-medium text-zinc-800">
              Code pays
            </label>
            <input
              id="provider-country"
              autoComplete="country"
              maxLength={2}
              {...form.register("country")}
              className="h-11 w-full rounded-lg border border-zinc-300 px-3 text-sm uppercase outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="FR"
            />
            {form.formState.errors.country && <p className="text-xs text-red-600">{form.formState.errors.country.message}</p>}
          </div>

          <div className="space-y-2 md:col-span-2">
            <label htmlFor="provider-website" className="text-sm font-medium text-zinc-800">
              Site officiel
            </label>
            <input
              id="provider-website"
              type="url"
              autoComplete="url"
              maxLength={500}
              {...form.register("officialWebsite")}
              className="h-11 w-full rounded-lg border border-zinc-300 px-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="https://www.exemple.fr"
            />
            <p className="text-xs text-zinc-500">L’API exige une adresse HTTPS valide.</p>
            {form.formState.errors.officialWebsite && (
              <p className="text-xs text-red-600">{form.formState.errors.officialWebsite.message}</p>
            )}
          </div>
        </div>
      </div>

      <div className="flex flex-col-reverse gap-3 border-t border-zinc-100 bg-zinc-50/70 px-5 py-4 sm:flex-row sm:justify-between sm:px-8">
        <Button type="button" variant="outline" onClick={() => router.back()} disabled={submitting}>
          <ArrowLeft size={16} className="mr-2" /> Annuler
        </Button>
        <Button type="submit" disabled={submitting} className="bg-blue-600 text-white hover:bg-blue-700">
          {submitting ? <LoaderCircle size={16} className="mr-2 animate-spin" /> : <Save size={16} className="mr-2" />}
          {submitting ? "Enregistrement…" : isEditing ? "Enregistrer les modifications" : "Créer le fournisseur"}
        </Button>
      </div>
    </form>
  );
}
