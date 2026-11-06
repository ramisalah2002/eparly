"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowUpRight,
  Building2,
  ExternalLink,
  Plus,
  RefreshCw,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { ErrorBanner } from "@/components/common/ErrorBanner";
import { Pagination } from "@/components/catalog/Pagination";
import { apiRequest, HttpError } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import type { AdminPageResponse, OfferProviderResponse, OfferSector, ProviderStatus } from "@/types/offer";

const PAGE_SIZE = 20;

const sectorLabels: Record<OfferSector, string> = {
  STREAMING: "Streaming",
  MOBILE_PLAN: "Forfait mobile",
  INTERNET_BOX: "Internet / box",
};

const statusLabels: Record<ProviderStatus, string> = {
  ACTIVE: "Actif",
  INACTIVE: "Inactif",
};

function ProviderStatusBadge({ status }: { status: ProviderStatus }) {
  const active = status === "ACTIVE";
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${
      active ? "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200" : "bg-zinc-100 text-zinc-600 ring-1 ring-zinc-200"
    }`}>
      <span className={`size-1.5 rounded-full ${active ? "bg-emerald-500" : "bg-zinc-400"}`} />
      {statusLabels[status]}
    </span>
  );
}

export default function ProvidersPage() {
  return (
    <Suspense fallback={<div className="h-96 animate-pulse rounded-2xl border border-zinc-200 bg-white" />}>
      <ProvidersListPage />
    </Suspense>
  );
}

function ProvidersListPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [providers, setProviders] = useState<OfferProviderResponse[]>([]);
  const [page, setPage] = useState(() => Math.max(0, Number(searchParams.get("page")) || 0));
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | ProviderStatus>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<{ status?: number; message: string } | null>(null);

  const fetchProviders = useCallback(async () => {
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      const response = await apiRequest<AdminPageResponse<OfferProviderResponse>>(
        `/api/admin/catalog/providers?${params.toString()}`,
      );
      setError(null);
      setProviders(response.items ?? []);
      setTotalElements(response.totalElements ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch (cause) {
      const failure = cause instanceof HttpError ? cause : null;
      const message = failure?.status === 403
        ? "Votre compte ne dispose pas de la permission CATALOG_ADMIN."
        : failure?.status === 401
          ? "Votre session a expiré. Reconnectez-vous."
          : failure?.message ?? "Impossible de charger les fournisseurs.";
      setError({ status: failure?.status, message });
    } finally {
      setLoading(false);
    }
  }, [page]);

  const refreshProviders = () => {
    setLoading(true);
    setError(null);
    void fetchProviders();
  };

  useEffect(() => {
    async function load() {
      await fetchProviders();
    }
    void load();
  }, [fetchProviders]);

  const visibleProviders = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase("fr");
    return providers.filter((provider) => {
      const matchesName = !normalizedSearch || provider.name.toLocaleLowerCase("fr").includes(normalizedSearch);
      const matchesStatus = statusFilter === "ALL" || provider.status === statusFilter;
      return matchesName && matchesStatus;
    });
  }, [providers, search, statusFilter]);

  const createdProviderId = searchParams.get("created");

  const handlePageChange = (nextPage: number) => {
    setLoading(true);
    setError(null);
    setPage(nextPage);
    const params = new URLSearchParams(searchParams.toString());
    params.set("page", String(nextPage));
    router.replace(`/catalog/providers?${params.toString()}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-medium uppercase tracking-[0.16em] text-blue-700">
            <Building2 size={15} /> Catalogue commercial
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 sm:text-3xl">Fournisseurs</h1>
          <p className="mt-1 text-sm text-zinc-500">
            Gérez les fournisseurs et accédez à leurs offres et à leur historique administratif.
          </p>
        </div>
        <Button
          onClick={() => router.push("/catalog/providers/new")}
          className="h-10 shrink-0 bg-blue-600 text-white shadow-sm hover:bg-blue-700"
        >
          <Plus size={17} className="mr-2" /> Nouveau fournisseur
        </Button>
      </div>

      {createdProviderId && !loading && (
        <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          Fournisseur créé avec succès.{" "}
          <Link className="font-semibold underline underline-offset-2" href={`/catalog/providers/${createdProviderId}`}>
            Consulter sa fiche
          </Link>
        </div>
      )}

      {error && (
        <ErrorBanner
          title={error.status ? `Erreur ${error.status}` : "Chargement impossible"}
          message={error.message}
          retry={refreshProviders}
          retryLabel="Réessayer"
          onDismiss={() => setError(null)}
        />
      )}

      <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-zinc-100 p-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <div>
            <p className="text-sm font-semibold text-zinc-900">
              {loading ? "Chargement…" : `${totalElements} fournisseur${totalElements > 1 ? "s" : ""}`}
            </p>
            <p className="mt-0.5 text-xs text-zinc-500">
              Données réelles de l’API d’administration · tri alphabétique
            </p>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={refreshProviders} disabled={loading}>
            <RefreshCw size={15} className={`mr-2 ${loading ? "animate-spin" : ""}`} /> Actualiser
          </Button>
        </div>

        <div className="grid gap-3 border-b border-zinc-100 bg-zinc-50/60 p-4 sm:grid-cols-[minmax(0,1fr)_220px] sm:px-5">
          <label className="relative block">
            <span className="sr-only">Rechercher un fournisseur</span>
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Rechercher par nom…"
              className="h-10 w-full rounded-lg border border-zinc-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </label>
          <label className="relative block">
            <span className="sr-only">Filtrer par statut</span>
            <SlidersHorizontal size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as "ALL" | ProviderStatus)}
              className="h-10 w-full appearance-none rounded-lg border border-zinc-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            >
              <option value="ALL">Tous les statuts</option>
              <option value="ACTIVE">Actifs</option>
              <option value="INACTIVE">Inactifs</option>
            </select>
          </label>
        </div>
        <p className="border-b border-zinc-100 px-4 py-2 text-xs text-zinc-500 sm:px-5">
          La recherche et le statut s’appliquent aux {providers.length} résultat{providers.length === 1 ? "" : "s"} de cette page. L’API ne fournit pas de filtres fournisseur.
        </p>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-white text-xs uppercase tracking-wide text-zinc-500">
              <tr>
                <th scope="col" className="px-5 py-3 font-medium">Fournisseur</th>
                <th scope="col" className="px-5 py-3 font-medium">Secteur</th>
                <th scope="col" className="px-5 py-3 font-medium">Site officiel</th>
                <th scope="col" className="px-5 py-3 font-medium">Statut</th>
                <th scope="col" className="px-5 py-3 font-medium">Modifié le</th>
                <th scope="col" className="px-5 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100">
              {loading && Array.from({ length: 5 }, (_, index) => (
                <tr key={`skeleton-${index}`} aria-hidden="true">
                  {Array.from({ length: 6 }, (_, cell) => (
                    <td key={cell} className="px-5 py-4"><div className="h-4 animate-pulse rounded bg-zinc-100" /></td>
                  ))}
                </tr>
              ))}
              {!loading && visibleProviders.map((provider) => (
                <tr key={provider.id} className="transition-colors hover:bg-zinc-50/80">
                  <td className="px-5 py-4">
                    <Link href={`/catalog/providers/${provider.id}`} className="font-semibold text-zinc-900 hover:text-blue-700">
                      {provider.name}
                    </Link>
                    <p className="mt-1 text-xs text-zinc-500">{provider.country}</p>
                  </td>
                  <td className="px-5 py-4 text-zinc-600">{sectorLabels[provider.sector] ?? provider.sector}</td>
                  <td className="px-5 py-4">
                    <a href={provider.officialWebsite} target="_blank" rel="noreferrer" className="inline-flex max-w-48 items-center gap-1.5 truncate text-blue-700 hover:underline">
                      <span className="truncate">{provider.officialWebsite}</span><ExternalLink size={13} className="shrink-0" />
                    </a>
                  </td>
                  <td className="px-5 py-4"><ProviderStatusBadge status={provider.status} /></td>
                  <td className="px-5 py-4 text-zinc-500">{formatDateTime(provider.updatedAt)}</td>
                  <td className="px-5 py-4 text-right">
                    <Link href={`/catalog/providers/${provider.id}`} className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-blue-700 hover:bg-blue-50">
                      Détails <ArrowUpRight size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && visibleProviders.length === 0 && (
            <div className="px-6 py-14 text-center">
              <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-zinc-100 text-zinc-500">
                <Building2 size={22} />
              </div>
              <h2 className="mt-4 text-sm font-semibold text-zinc-900">
                {providers.length === 0 ? "Aucun fournisseur" : "Aucun résultat sur cette page"}
              </h2>
              <p className="mx-auto mt-1 max-w-md text-sm text-zinc-500">
                {providers.length === 0
                  ? "L’API n’a retourné aucun fournisseur pour cette page."
                  : "Modifiez les filtres ou passez à une autre page pour poursuivre la recherche."}
              </p>
              {providers.length > 0 && (
                <Button variant="outline" size="sm" className="mt-4" onClick={() => { setSearch(""); setStatusFilter("ALL"); }}>
                  Effacer les filtres
                </Button>
              )}
            </div>
          )}
        </div>
        <div className="border-t border-zinc-100">
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={PAGE_SIZE}
            onPageChange={handlePageChange}
          />
        </div>
      </section>
    </div>
  );
}
