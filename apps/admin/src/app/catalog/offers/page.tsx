"use client";

export const dynamic = "force-dynamic";

import { useEffect, useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { apiRequest } from "@/lib/api";
import { ErrorBanner } from "@/components/common/ErrorBanner";
import { OfferTable } from "@/components/catalog/OfferTable";
import { OfferFilters } from "@/components/catalog/OfferFilters";
import { Pagination } from "@/components/catalog/Pagination";
import { CommercialOfferResponse, AdminPageResponse, OfferMetadataResponse, OfferProviderResponse } from "@/types/offer";

export default function OffersPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [offers, setOffers] = useState<CommercialOfferResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page] = useState(Number(searchParams.get("page")) || 0);
  const [size] = useState(20);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [providers, setProviders] = useState<Array<{ id: string; name: string }>>([]);
  const [sectors, setSectors] = useState<string[]>([]);
  const [statuses, setStatuses] = useState<string[]>([]);

  const fetchMetadata = useCallback(async () => {
    try {
      const meta = await apiRequest<OfferMetadataResponse>("/api/offers/metadata");
      setSectors(meta.sectors || []);
      setStatuses(meta.publicStatuses || []);
    } catch {
      // Metadata is optional; filters still work with empty lists
    }
  }, []);

  const fetchProviders = useCallback(async () => {
    try {
      const data = await apiRequest<OfferProviderResponse[]>("/api/offers/providers");
      setProviders(
        data.map((p) => ({ id: p.id, name: p.name })),
      );
    } catch {
      // Providers optional for filtering
    }
  }, []);

  const fetchOffers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const providerId = searchParams.get("providerId") ?? "";
      const sector = searchParams.get("sector") ?? "";
      const status = searchParams.get("status") ?? "";

      const params = new URLSearchParams({
        page: String(page),
        size: String(size),
      });
      if (providerId) params.set("providerId", providerId);
      if (sector) params.set("sector", sector);
      if (status) params.set("status", status);

      const data = await apiRequest<AdminPageResponse<CommercialOfferResponse>>(
        `/api/admin/catalog/offers?${params.toString()}`,
      );
      setOffers(data.items || []);
      setTotalElements(data.totalElements || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      const e = err as { status?: number; message?: string };
      if (e.status === 401 || e.status === 403) {
        router.push("/login");
        return;
      }
      setError(e.message || "Echec du chargement des offres.");
    } finally {
      setLoading(false);
    }
  }, [page, size, searchParams, router]);

  useEffect(() => {
    async function load() {
      await fetchMetadata();
      await fetchProviders();
      await fetchOffers();
    }
    load();
  }, [fetchMetadata, fetchProviders, fetchOffers]);

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("page", String(newPage));
    router.push(`?${params.toString()}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">Offres commerciales</h1>
          <p className="text-sm text-zinc-500">
            Consultez, filtrez et paginationnez les offres du catalogue PostgreSQL gérées par l'administration.
          </p>
        </div>
      </div>

      {error && (
        <ErrorBanner
          title="Echec du chargement des offres"
          message={error}
          status={undefined}
          onDismiss={() => setError(null)}
          retry={fetchOffers}
          retryLabel="Réessayer"
        />
      )}

      <OfferFilters
        onRefresh={fetchOffers}
        providers={providers}
        sectors={sectors}
        statuses={statuses}
      />

      <div className="rounded-lg border border-zinc-200 bg-white shadow-sm">
        <OfferTable
          offers={offers}
          loading={loading}
          empty={!loading && offers.length === 0}
        />
      </div>

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        size={size}
        onPageChange={handlePageChange}
      />
    </div>
  );
}
