"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ChevronRight, ShieldAlert } from "lucide-react";
import { ProviderForm } from "@/components/providers/ProviderForm";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api";
import type { OfferProviderResponse } from "@/types/offer";

export default function EditProviderPage() {
  const { providerId } = useParams<{ providerId: string }>();
  const router = useRouter();
  const [provider, setProvider] = useState<OfferProviderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadProvider = useCallback(async () => {
    try {
      const result = await apiRequest<OfferProviderResponse>(
        `/api/admin/catalog/providers/${providerId}`,
      );
      setError(null);
      setProvider(result);
    } catch (cause) {
      const failure = cause as { status?: number; message?: string };
      setError(failure.status === 404
        ? "Ce fournisseur n’existe plus."
        : failure.message ?? "Impossible de charger le fournisseur.");
    } finally {
      setLoading(false);
    }
  }, [providerId]);

  useEffect(() => {
    async function load() {
      await loadProvider();
    }
    void load();
  }, [loadProvider]);

  if (loading) {
    return <div className="h-72 animate-pulse rounded-2xl border border-zinc-200 bg-white" aria-label="Chargement du fournisseur" />;
  }

  if (!provider) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-8 text-center">
        <ShieldAlert size={30} className="mx-auto text-red-500" />
        <p className="mt-3 text-sm text-zinc-700">{error ?? "Fournisseur introuvable."}</p>
        <div className="mt-4 flex justify-center gap-2">
          <Button variant="outline" onClick={() => void loadProvider()}>Réessayer</Button>
          <Button onClick={() => router.push("/catalog/providers")}>Retour aux fournisseurs</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <nav aria-label="Fil d’Ariane" className="flex items-center gap-2 text-sm text-zinc-500">
        <Link href="/catalog/providers" className="hover:text-zinc-900">Fournisseurs</Link>
        <ChevronRight size={15} />
        <Link href={`/catalog/providers/${provider.id}`} className="max-w-48 truncate hover:text-zinc-900">{provider.name}</Link>
        <ChevronRight size={15} />
        <span className="font-medium text-zinc-900">Modifier</span>
      </nav>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-zinc-900">Modifier le fournisseur</h1>
        <p className="mt-1 text-sm text-zinc-500">
          Les offres liées ne sont jamais supprimées. Toute modification effective peut marquer les offres publiées comme à réviser.
        </p>
      </div>
      <ProviderForm key={`${provider.id}-${provider.version}`} provider={provider} />
    </div>
  );
}
