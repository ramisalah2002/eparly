"use client";

import {
  SlidersHorizontal,
  RefreshCw,
  Search,
  X,
} from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo, useState } from "react";

export interface OfferFiltersProps {
  onRefresh: () => void;
  providers: Array<{ id: string; name: string }>;
  sectors: string[];
  statuses: string[];
}

export function OfferFilters({
  onRefresh,
  providers,
  sectors,
  statuses,
}: OfferFiltersProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [providerId, setProviderId] = useState(searchParams.get("providerId") ?? "");
  const [sector, setSector] = useState(searchParams.get("sector") ?? "");
  const [status, setStatus] = useState(searchParams.get("status") ?? "");

  const setParams = useCallback(
    (updates: Record<string, string | null>) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [key, value] of Object.entries(updates)) {
        if (value === null || value === "") {
          params.delete(key);
        } else {
          params.set(key, value);
        }
      }
      router.push(`?${params.toString()}`);
    },
    [router, searchParams],
  );

  const isFilterActive = useMemo(
    () =>
      !!query || !!providerId || !!sector || !!status,
    [query, providerId, sector, status],
  );

  const clearFilters = useCallback(() => {
    setQuery("");
    setProviderId("");
    setSector("");
    setStatus("");
    router.push("?");
  }, [router]);

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
      <div className="flex w-full flex-1 items-center gap-2 sm:w-auto">
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
          <input
            type="search"
            placeholder="Rechercher une offre ou un fournisseur..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                setParams({ q: query, providerId: null, sector: null, status: null });
              }
            }}
            className="block w-full pl-10 pr-3 py-2 text-sm border border-zinc-300 rounded-lg focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none"
          />
        </div>
        {query && (
          <button
            type="button"
            onClick={() => {
              setQuery("");
              setParams({ q: null, providerId: null, sector: null, status: null });
            }}
            className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-600"
          >
            <X size={16} />
          </button>
        )}
      </div>

      <div className="flex w-full flex-1 gap-3 sm:w-auto overflow-x-auto">
        <select
          value={providerId}
          onChange={(e) => {
            setProviderId(e.target.value);
            setParams({ providerId: e.target.value || null, q: null, sector: null, status: null });
          }}
          className="text-sm border border-zinc-300 rounded-lg bg-white px-3 py-2 focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none min-w-[200px]"
        >
          <option value="">Tous les fournisseurs</option>
          {providers.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>

        <select
          value={sector}
          onChange={(e) => {
            setSector(e.target.value);
            setParams({ sector: e.target.value || null, q: null, providerId: null, status: null });
          }}
          className="text-sm border border-zinc-300 rounded-lg bg-white px-3 py-2 focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none min-w-[160px]"
        >
          <option value="">Tous les secteurs</option>
          {sectors.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>

        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setParams({ status: e.target.value || null, q: null, providerId: null, sector: null });
          }}
          className="text-sm border border-zinc-300 rounded-lg bg-white px-3 py-2 focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none min-w-[160px]"
        >
          <option value="">Tous les statuts</option>
          {statuses.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      <div className="flex w-full items-center justify-between gap-3 sm:w-auto sm:justify-end">
        {isFilterActive && (
          <button
            type="button"
            onClick={clearFilters}
            className="flex items-center gap-2 text-xs font-medium text-zinc-600 bg-zinc-100 hover:bg-zinc-200 rounded-lg px-3 py-1.5 transition-colors"
          >
            <X size={14} /> Effacer les filtres
          </button>
        )}
        <div className="flex items-center gap-2 text-xs text-zinc-500 hidden sm:flex">
          <SlidersHorizontal size={14} className="mr-1" />
          Filtres
        </div>
        <button
          type="button"
          onClick={onRefresh}
          className="flex items-center gap-2 text-sm font-medium text-zinc-600 border border-zinc-300 hover:bg-zinc-100 rounded-lg px-3 py-2 transition-colors"
        >
          <RefreshCw size={16} className="text-zinc-500" />
          Actualiser
        </button>
      </div>
    </div>
  );
}
