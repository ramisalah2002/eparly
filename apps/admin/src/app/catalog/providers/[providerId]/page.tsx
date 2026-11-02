"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { Suspense } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowUpRight,
  Building2,
  Check,
  ChevronRight,
  Clock3,
  ExternalLink,
  FileClock,
  Globe2,
  LoaderCircle,
  Pencil,
  Power,
  RefreshCw,
  ShieldAlert,
  Tag,
  X,
} from "lucide-react";
import { ErrorBanner } from "@/components/common/ErrorBanner";
import { Pagination } from "@/components/catalog/Pagination";
import { Button } from "@/components/ui/button";
import { apiRequest, HttpError } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import type {
  AdminPageResponse,
  CatalogAuditResponse,
  CommercialOfferResponse,
  OfferProviderResponse,
  OfferSector,
  ProviderStatus,
} from "@/types/offer";

const OFFER_PAGE_SIZE = 20;

const sectorLabels: Record<OfferSector, string> = {
  STREAMING: "Streaming",
  MOBILE_PLAN: "Forfait mobile",
  INTERNET_BOX: "Internet / box",
};

const offerStatusLabels: Record<string, string> = {
  DRAFT: "Brouillon",
  VERIFIED: "Publiée",
  STALE: "À réviser",
  EXPIRED: "Expirée",
  ARCHIVED: "Archivée",
};

function StatusBadge({ status }: { status: ProviderStatus }) {
  const active = status === "ACTIVE";
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
      active ? "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200" : "bg-zinc-100 text-zinc-600 ring-1 ring-zinc-200"
    }`}>
      <span className={`size-1.5 rounded-full ${active ? "bg-emerald-500" : "bg-zinc-400"}`} />
      {active ? "Actif" : "Inactif"}
    </span>
  );
}

function ApiError({ error, retry }: { error: { status?: number; message: string }; retry?: () => void }) {
  return (
    <ErrorBanner
      title={error.status ? `Erreur ${error.status}` : "Erreur"}
      message={error.message}
      retry={retry}
      retryLabel={retry ? "Réessayer" : undefined}
    />
  );
}

export default function ProviderDetailPage() {
  return (
    <Suspense fallback={<div className="space-y-5"><div className="h-6 w-56 animate-pulse rounded bg-zinc-200" /><div className="h-48 animate-pulse rounded-2xl border border-zinc-200 bg-white" /></div>}>
      <ProviderDetailContent />
    </Suspense>
  );
}

function ProviderDetailContent() {
  const { providerId } = useParams<{ providerId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [provider, setProvider] = useState<OfferProviderResponse | null>(null);
  const [offers, setOffers] = useState<CommercialOfferResponse[]>([]);
  const [offersTotal, setOffersTotal] = useState(0);
  const [offersPages, setOffersPages] = useState(0);
  const [offersPage, setOffersPage] = useState(0);
  const [audit, setAudit] = useState<CatalogAuditResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [offersError, setOffersError] = useState<{ status?: number; message: string } | null>(null);
  const [auditError, setAuditError] = useState<{ status?: number; message: string } | null>(null);
  const [actionError, setActionError] = useState<{ status?: number; message: string } | null>(null);
  const [changingStatus, setChangingStatus] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);

  const loadProvider = useCallback(async () => {
    try {
      const result = await apiRequest<OfferProviderResponse>(
        `/api/admin/catalog/providers/${providerId}`,
      );
      setActionError(null);
      setProvider(result);
    } catch (cause) {
      const failure = cause instanceof HttpError ? cause : null;
      setActionError({
        status: failure?.status,
        message: failure?.status === 404
          ? "Ce fournisseur n’existe pas ou a été supprimé."
          : failure?.status === 403
            ? "Votre compte ne dispose pas de la permission CATALOG_ADMIN."
            : failure?.message ?? "Impossible de charger le fournisseur.",
      });
    } finally {
      setLoading(false);
    }
  }, [providerId]);

  const loadOffers = useCallback(async () => {
    try {
      const params = new URLSearchParams({
        providerId,
        page: String(offersPage),
        size: String(OFFER_PAGE_SIZE),
      });
      const result = await apiRequest<AdminPageResponse<CommercialOfferResponse>>(
        `/api/admin/catalog/offers?${params.toString()}`,
      );
      setOffersError(null);
      setOffers(result.items ?? []);
      setOffersTotal(result.totalElements ?? 0);
      setOffersPages(result.totalPages ?? 0);
    } catch (cause) {
      const failure = cause instanceof HttpError ? cause : null;
      setOffersError({ status: failure?.status, message: failure?.message ?? "Impossible de charger les offres associées." });
    }
  }, [providerId, offersPage]);

  const loadAudit = useCallback(async () => {
    try {
      const result = await apiRequest<CatalogAuditResponse[]>(
        `/api/admin/catalog/providers/${providerId}/audit`,
      );
      setAuditError(null);
      setAudit(result ?? []);
    } catch (cause) {
      const failure = cause instanceof HttpError ? cause : null;
      setAuditError({ status: failure?.status, message: failure?.message ?? "Impossible de charger l’historique administratif." });
    }
  }, [providerId]);

  useEffect(() => {
    async function load() {
      await loadProvider();
    }
    void load();
  }, [loadProvider]);

  useEffect(() => {
    async function load() {
      await loadOffers();
    }
    void load();
  }, [loadOffers]);

  useEffect(() => {
    async function load() {
      await loadAudit();
    }
    void load();
  }, [loadAudit]);

  const changeStatus = async (status: ProviderStatus) => {
    if (!provider) return;
    setChangingStatus(true);
    setActionError(null);
    try {
      const updated = await apiRequest<OfferProviderResponse>(
        `/api/admin/catalog/providers/${provider.id}`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ expectedVersion: provider.version, status }),
        },
      );
      setProvider(updated);
      setConfirmDeactivate(false);
      await Promise.all([loadOffers(), loadAudit()]);
    } catch (cause) {
      const failure = cause instanceof HttpError ? cause : null;
      setActionError({
        status: failure?.status,
        message: failure?.status === 409
          ? "Le fournisseur a été modifié depuis son chargement. Actualisez la fiche avant de réessayer."
          : failure?.status === 400 || failure?.status === 422
            ? `La modification a été refusée : ${failure.message}`
            : failure?.status === 403
              ? "Votre compte ne dispose pas de la permission CATALOG_ADMIN."
              : failure?.status === 401
                ? "Votre session a expiré. Reconnectez-vous."
                : failure?.message ?? "La modification du statut a échoué.",
      });
    } finally {
      setChangingStatus(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-5" aria-label="Chargement du fournisseur">
        <div className="h-6 w-56 animate-pulse rounded bg-zinc-200" />
        <div className="h-48 animate-pulse rounded-2xl border border-zinc-200 bg-white" />
        <div className="h-72 animate-pulse rounded-2xl border border-zinc-200 bg-white" />
      </div>
    );
  }

  if (!provider) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-8 text-center">
        <ShieldAlert size={32} className="mx-auto text-red-500" />
        <p className="mt-3 text-sm text-zinc-700">{actionError?.message ?? "Fournisseur introuvable."}</p>
        <div className="mt-4 flex justify-center gap-2">
          <Button variant="outline" onClick={() => void loadProvider()}>Réessayer</Button>
          <Button onClick={() => router.push("/catalog/providers")}>Retour aux fournisseurs</Button>
        </div>
      </div>
    );
  }

  const updatedNotice = searchParams.get("updated") === "1";

  return (
    <div className="space-y-6">
      <nav aria-label="Fil d’Ariane" className="flex flex-wrap items-center gap-2 text-sm text-zinc-500">
        <Link href="/catalog/providers" className="hover:text-zinc-900">Fournisseurs</Link>
        <ChevronRight size={15} />
        <span className="max-w-56 truncate font-medium text-zinc-900">{provider.name}</span>
      </nav>

      {updatedNotice && (
        <div role="status" className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <Check size={17} /> Les informations du fournisseur ont été mises à jour.
        </div>
      )}
      {actionError && <ApiError error={actionError} retry={() => void loadProvider()} />}

      <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
        <div className="bg-gradient-to-r from-blue-50 via-white to-white p-5 sm:p-8">
          <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex min-w-0 items-start gap-4">
              <div className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-blue-100 text-blue-700">
                <Building2 size={26} />
              </div>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-3">
                  <h1 className="break-words text-2xl font-bold tracking-tight text-zinc-900 sm:text-3xl">{provider.name}</h1>
                  <StatusBadge status={provider.status} />
                </div>
                <p className="mt-2 text-sm text-zinc-500">
                  {sectorLabels[provider.sector] ?? provider.sector} <span className="px-1">·</span> {provider.country}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => router.push(`/catalog/providers/${provider.id}/edit`)}>
                <Pencil size={15} className="mr-2" /> Modifier
              </Button>
              {provider.status === "ACTIVE" ? (
                <Button variant="outline" onClick={() => setConfirmDeactivate(true)} className="border-amber-300 text-amber-800 hover:bg-amber-50">
                  <Power size={15} className="mr-2" /> Désactiver
                </Button>
              ) : (
                <Button
                  onClick={() => void changeStatus("ACTIVE")}
                  disabled={changingStatus}
                  className="bg-emerald-600 text-white hover:bg-emerald-700"
                >
                  {changingStatus ? <LoaderCircle size={15} className="mr-2 animate-spin" /> : <Power size={15} className="mr-2" />}
                  Activer
                </Button>
              )}
              <Button variant="outline" onClick={() => { void loadProvider(); void loadOffers(); void loadAudit(); }}>
                <RefreshCw size={15} className="mr-2" /> Actualiser
              </Button>
            </div>
          </div>
        </div>

        <div className="grid gap-px border-t border-zinc-100 bg-zinc-100 sm:grid-cols-2 lg:grid-cols-4">
          <div className="bg-white p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">Site officiel</p>
            <a href={provider.officialWebsite} target="_blank" rel="noreferrer" className="mt-2 inline-flex max-w-full items-center gap-1.5 truncate text-sm font-medium text-blue-700 hover:underline">
              <Globe2 size={15} className="shrink-0" />
              <span className="truncate">{provider.officialWebsite}</span>
              <ExternalLink size={13} className="shrink-0" />
            </a>
          </div>
          <div className="bg-white p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">Secteur</p>
            <p className="mt-2 flex items-center gap-2 text-sm font-medium text-zinc-900"><Tag size={15} className="text-zinc-400" />{sectorLabels[provider.sector] ?? provider.sector}</p>
          </div>
          <div className="bg-white p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">Créé le</p>
            <p className="mt-2 flex items-center gap-2 text-sm text-zinc-700"><Clock3 size={15} className="text-zinc-400" />{formatDateTime(provider.createdAt)}</p>
          </div>
          <div className="bg-white p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">Dernière modification</p>
            <p className="mt-2 flex items-center gap-2 text-sm text-zinc-700"><Clock3 size={15} className="text-zinc-400" />{formatDateTime(provider.updatedAt)}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-zinc-100 px-5 py-3 text-xs text-zinc-500">
          <span>Version {provider.version}</span>
          <Link href="/catalog/offers" className="inline-flex items-center gap-1 font-medium text-blue-700 hover:underline">
            Retour à toutes les offres <ArrowUpRight size={13} />
          </Link>
        </div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-zinc-100 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-semibold text-zinc-900">Offres associées</h2>
            <p className="mt-1 text-sm text-zinc-500">
              {offersTotal} offre{offersTotal > 1 ? "s" : ""} retournée{offersTotal > 1 ? "s" : ""} par l’API
            </p>
          </div>
          <Link href={`/catalog/offers?providerId=${encodeURIComponent(provider.id)}`} className="inline-flex items-center gap-1.5 text-sm font-semibold text-blue-700 hover:underline">
            Voir la liste filtrée <ArrowUpRight size={15} />
          </Link>
        </div>
        {offersError && <div className="p-4"><ApiError error={offersError} retry={() => void loadOffers()} /></div>}
        {!offersError && (
          <>
            {offers.length ? (
              <div className="divide-y divide-zinc-100">
                {offers.map((offer) => (
                  <Link key={offer.id} href={`/catalog/offers/${offer.id}`} className="flex flex-col gap-2 px-5 py-4 transition hover:bg-zinc-50 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-zinc-900">{offer.commercialName}</p>
                      <p className="mt-1 text-xs text-zinc-500">{offer.sector} · mise à jour {formatDateTime(offer.updatedAt)}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                      <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-xs text-zinc-600">
                        {offerStatusLabels[offer.publicationStatus] ?? offer.publicationStatus}
                      </span>
                      <ArrowUpRight size={15} className="text-zinc-400" />
                    </div>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="px-6 py-10 text-center">
                <p className="text-sm font-medium text-zinc-800">Aucune offre associée</p>
                <p className="mt-1 text-xs text-zinc-500">Aucune offre n’a été retournée pour ce fournisseur.</p>
              </div>
            )}
            {offersPages > 1 && (
              <div className="border-t border-zinc-100">
                <Pagination
                  page={offersPage}
                  totalPages={offersPages}
                  totalElements={offersTotal}
                  size={OFFER_PAGE_SIZE}
                  onPageChange={setOffersPage}
                />
              </div>
            )}
          </>
        )}
      </section>

      <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-zinc-100 p-5">
          <div>
            <h2 className="flex items-center gap-2 text-base font-semibold text-zinc-900"><FileClock size={18} className="text-zinc-500" /> Historique administratif</h2>
            <p className="mt-1 text-sm text-zinc-500">Actions enregistrées par le backend.</p>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={() => void loadAudit()}>
            <RefreshCw size={14} className="mr-2" /> Actualiser
          </Button>
        </div>
        {auditError && <div className="p-4"><ApiError error={auditError} retry={() => void loadAudit()} /></div>}
        {!auditError && audit.length === 0 && (
          <div className="px-6 py-10 text-center text-sm text-zinc-500">Aucune action administrative retournée.</div>
        )}
        {!auditError && audit.length > 0 && (
          <ol className="divide-y divide-zinc-100">
            {audit.map((entry) => (
              <li key={entry.id} className="px-5 py-4">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p className="text-sm font-semibold text-zinc-900">{entry.action}</p>
                    <p className="mt-1 text-xs text-zinc-500">
                      {entry.resourceType} · acteur {entry.actorUserId ?? "non renseigné"}
                    </p>
                  </div>
                  <time className="shrink-0 text-xs text-zinc-500">{formatDateTime(entry.occurredAt)}</time>
                </div>
                {entry.details && (
                  <details className="mt-3 rounded-lg bg-zinc-50 px-3 py-2">
                    <summary className="cursor-pointer text-xs font-medium text-zinc-600">Détails de l’action</summary>
                    <pre className="mt-2 overflow-x-auto whitespace-pre-wrap break-words text-xs text-zinc-700">
                      {JSON.stringify(entry.details, null, 2)}
                    </pre>
                  </details>
                )}
              </li>
            ))}
          </ol>
        )}
      </section>

      <Link href="/catalog/providers" className="inline-flex items-center gap-2 text-sm font-medium text-zinc-600 hover:text-zinc-900">
        <ArrowLeft size={15} /> Retour aux fournisseurs
      </Link>

      {confirmDeactivate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/40 p-4" role="presentation">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="deactivate-title"
            className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 id="deactivate-title" className="text-lg font-semibold text-zinc-900">Désactiver {provider.name} ?</h2>
                <p className="mt-2 text-sm leading-6 text-zinc-600">
                  Le backend masque les offres de ce fournisseur du catalogue public. Les offres et leurs données ne sont pas supprimées. Les offres publiées peuvent être marquées à réviser et ne seront pas republiées automatiquement en cas de réactivation.
                </p>
              </div>
              <button type="button" onClick={() => setConfirmDeactivate(false)} aria-label="Fermer" className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700">
                <X size={18} />
              </button>
            </div>
            {actionError && <div className="mt-4"><ApiError error={actionError} /></div>}
            <div className="mt-6 flex flex-col-reverse justify-end gap-2 sm:flex-row">
              <Button type="button" variant="outline" onClick={() => setConfirmDeactivate(false)} disabled={changingStatus}>Annuler</Button>
              <Button
                type="button"
                onClick={() => void changeStatus("INACTIVE")}
                disabled={changingStatus}
                className="bg-amber-700 text-white hover:bg-amber-800"
              >
                {changingStatus ? <LoaderCircle size={15} className="mr-2 animate-spin" /> : <Power size={15} className="mr-2" />}
                Confirmer la désactivation
              </Button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
