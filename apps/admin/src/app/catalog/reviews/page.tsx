"use client";

export const dynamic = "force-dynamic";

import { useEffect, useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { apiRequest } from "@/lib/api";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { ErrorBanner } from "@/components/common/ErrorBanner";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PriceFormatter } from "@/components/common/PriceFormatter";
import { formatDateTime, isFreshnessStale, truncate } from "@/lib/utils";
import {
  CommercialOfferResponse,
  AdminPageResponse,
  OfferEvidenceResponse,
} from "@/types/offer";
import { Button } from "@/components/ui/button";
import {
  ExternalLink,
  AlertTriangle,
  ShieldCheck,
} from "lucide-react";
import { VerifyEvidenceDialog } from "@/components/catalog/VerifyEvidenceDialog";

interface ReviewItem {
  offer: CommercialOfferResponse;
  blockers: string[];
  missingInfo: string[];
  pendingEvidence: boolean;
  staleEvidence: boolean;
}

export default function ReviewsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [items, setItems] = useState<ReviewItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page] = useState(Number(searchParams.get("page")) || 0);
  const [size] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [verifyEvidence, setVerifyEvidence] = useState<{ offerId: string; evidence: OfferEvidenceResponse } | null>(null);

  const fetchReviews = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Fetch multiple statuses that need review: DRAFT, STALE, EXPIRED
      // We fetch all offers without status filter, then identify review candidates client-side.
      const params = new URLSearchParams({
        page: String(page),
        size: String(size),
      });

      const data = await apiRequest<AdminPageResponse<CommercialOfferResponse>>(
        `/api/admin/catalog/offers?${params.toString()}`,
      );
      const allOffers = data.items || [];
      setTotalElements(data.totalElements || 0);
      setTotalPages(data.totalPages || 0);

      const reviewItems: ReviewItem[] = allOffers.map((offer) => {
        const pricing = offer.pricing;
        const latestEvidence = offer.evidence?.[0];
        const offerEvidence = offer.evidence?.filter((e) => e.evidenceScope === "OFFER") ?? [];
        const pendingEvidence = offer.evidence?.some((e) => e.verificationStatus === "PENDING") ?? false;

        const blockers: string[] = [];
        const missingInfo: string[] = [];

        // Business blockers
        if (offer.publicationStatus === "DRAFT") {
          blockers.push("Brouillon - n'a pas encore été publié");
        }
        if (offer.publicationStatus === "STALE") {
          blockers.push("Obsolète - n'est plus recommandée publiquement");
        }
        if (offer.publicationStatus === "EXPIRED") {
          blockers.push("Expirée - période de validité dépassée");
        }
        if (pendingEvidence) {
          blockers.push("Preuve(s) en attente de validation humaine");
        }
        if (latestEvidence && latestEvidence.verificationStatus === "INVALID") {
          blockers.push("Preuve invalide - doit étre révisée");
        }

        // Stale evidence (verified but older than freshness threshold)
        let evidenceStale = false;
        if (
          latestEvidence &&
          latestEvidence.verificationStatus === "VERIFIED" &&
          latestEvidence.verifiedAt &&
          isFreshnessStale(latestEvidence.verifiedAt)
        ) {
          blockers.push("Preuve non fraîche (vérification > 30 jours)");
          evidenceStale = true;
        }

        // Missing info
        if (!offer.characteristics) missingInfo.push("Caractéristiques");
        if (!offer.eligibilityConditions) missingInfo.push("Conditions d'éligibilité");
        if (!pricing || pricing.amountMinor == null) missingInfo.push("Tarif standard");
        if (pricing && pricing.dataUnknown) missingInfo.push("Données tarifaires incomplètes");
        if (!offerEvidence.length || offerEvidence.every((e) => e.verificationStatus !== "VERIFIED")) {
          missingInfo.push("Preuve OFFER vérifiée");
        }

        return {
          offer,
          blockers,
          missingInfo,
          pendingEvidence,
          staleEvidence: evidenceStale,
        };
      });

      // Prioritize: items with blockers first
      reviewItems.sort((a, b) => b.blockers.length - a.blockers.length);

      setItems(reviewItems);
    } catch (err) {
      const e = err as { status?: number; message?: string };
      if (e.status === 401 || e.status === 403) {
        router.push("/login");
        return;
      }
      setError(e.message || "Echec du chargement de la file de validation.");
    } finally {
      setLoading(false);
    }
  }, [page, size, router]);

  useEffect(() => {
    async function load() {
      await fetchReviews();
    }
    load();
  }, [fetchReviews]);

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("page", String(newPage));
    router.push(`?${params.toString()}`);
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <LoadingSpinner size={32} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-zinc-200 bg-white p-8 text-center">
        <AlertTriangle size={32} className="mx-auto text-red-400" />
        <h3 className="mt-2 text-sm font-medium text-zinc-900">Echec du chargement</h3>
        <p className="text-sm text-zinc-500">{error}</p>
        <Button onClick={fetchReviews} className="mt-4">Réessayer</Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">Centre de validation</h1>
          <p className="text-sm text-zinc-500">
            File des offres nécessitant une intervention administrative : preuves en attente, informations manquantes, blocages métiers.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-zinc-500">
            {items.filter((i) => i.blockers.length > 0).length} offre(s) à revoir
          </span>
        </div>
      </div>

      {error && (
        <ErrorBanner
          title="Erreur"
          message={error}
          onDismiss={() => setError(null)}
          retry={fetchReviews}
          retryLabel="Réessayer"
        />
      )}

      {items.length === 0 ? (
        <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-8 text-center">
          <ShieldCheck size={32} className="mx-auto text-green-500" />
          <h3 className="mt-2 text-sm font-medium text-zinc-900">Aucune offre à revoir</h3>
          <p className="text-sm text-zinc-500">
            Toutes les offres du catalogue sont à jour et nécessitent aucune intervention.
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-zinc-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-zinc-50 border-b border-zinc-200">
              <tr>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Offre</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Fournisseur</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Prix</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Source officielle</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Dernière consultation</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Infos manquantes</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Preuves</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Fraîcheur</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Blocages</th>
                <th className="p-3 text-left text-xs font-semibold text-zinc-600">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200">
              {items.map((item) => {
                const { offer, blockers, missingInfo, pendingEvidence, staleEvidence } = item;
                const pricing = offer.pricing;
                const latestEvidence = offer.evidence?.[0];
                const provider = offer.provider;

                return (
                  <tr key={offer.id} className="p-3 group">
                    <td>
                      <div className="text-sm font-medium text-zinc-900">{offer.commercialName}</div>
                      <div className="text-xs text-zinc-500">
                        {offer.publicationStatus} • ID {offer.id.slice(0, 8)}
                      </div>
                    </td>
                    <td className="text-sm text-zinc-900">{provider?.name ?? "Inconnu"}</td>
                    <td>
                      <PriceFormatter
                        amountMinor={pricing?.amountMinor ?? null}
                        currency={pricing?.currency ?? "EUR"}
                        billingPeriod={pricing?.billingPeriod ?? undefined}
                      />
                    </td>
                    <td>
                      {provider?.officialWebsite ? (
                        <a
                          href={provider.officialWebsite}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-sm text-blue-600 underline truncate max-w-[200px] block"
                        >
                          {truncate(provider.officialWebsite, 50)}
                          <ExternalLink size={12} className="inline ml-1 opacity-50" />
                        </a>
                      ) : (
                        <span className="text-sm text-zinc-400 italic">Aucune source</span>
                      )}
                    </td>
                    <td className="text-sm text-zinc-900">
                      {latestEvidence?.consultedAt
                        ? formatDateTime(latestEvidence.consultedAt)
                        : "Non consultée"}
                    </td>
                    <td>
                      {missingInfo.length > 0 ? (
                        <ul className="text-sm text-amber-700 list-disc pl-3 space-y-1">
                          {missingInfo.slice(0, 3).map((m) => (
                            <li key={m}>{m}</li>
                          ))}
                          {missingInfo.length > 3 && <span>+{missingInfo.length - 3}</span>}
                        </ul>
                      ) : (
                        <span className="text-sm text-zinc-500">—</span>
                      )}
                    </td>
                    <td>
                      {latestEvidence ? (
                        <StatusBadge status={latestEvidence.verificationStatus} />
                      ) : (
                        <span className="text-xs text-zinc-400 italic">Aucune</span>
                      )}
                      {pendingEvidence && (
                        <span className="ml-1 text-xs text-blue-600">En attente</span>
                      )}
                    </td>
                    <td>
                      {staleEvidence ? (
                        <span className="text-xs text-orange-700 font-medium">Non fraîche</span>
                      ) : latestEvidence?.verificationStatus === "VERIFIED" ? (
                        <span className="text-xs text-green-700">Fraîche</span>
                      ) : (
                        <span className="text-xs text-zinc-500">—</span>
                      )}
                    </td>
                    <td>
                      {blockers.length > 0 ? (
                        <ul className="text-sm text-red-700 list-disc pl-3 space-y-1">
                          {blockers.slice(0, 2).map((b) => (
                            <li key={b}>{truncate(b, 35)}</li>
                          ))}
                          {blockers.length > 2 && <span>+{blockers.length - 2}</span>}
                        </ul>
                      ) : (
                        <span className="text-sm text-green-700">Aucun</span>
                      )}
                    </td>
                    <td>
                      <div className="flex items-center gap-2">
                        {latestEvidence?.verificationStatus === "PENDING" && (
                          <Button
                            size="sm"
                            onClick={() => {
                              setVerifyEvidence({ offerId: offer.id, evidence: latestEvidence });
                              setVerifyOpen(true);
                            }}
                          >
                            Valider
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => router.push(`/catalog/offers/${offer.id}`)}
                        >
                          Détails
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between border-t border-zinc-200 bg-zinc-50 px-4 py-3">
        <p className="text-xs text-zinc-500">
          Page {page + 1} sur {totalPages} • {totalElements} offre(s)
        </p>
        <div className="flex items-center gap-1">
          <Button
            size="sm"
            onClick={() => handlePageChange(Math.max(0, page - 1))}
            disabled={page === 0}
          >
            Précédent
          </Button>
          <Button
            size="sm"
            onClick={() => handlePageChange(Math.min(totalPages - 1, page + 1))}
            disabled={page >= totalPages - 1}
          >
            Suivant
          </Button>
        </div>
      </div>

      {verifyOpen && verifyEvidence && (
        <VerifyEvidenceDialog
          open={verifyOpen}
          onClose={() => {
            setVerifyOpen(false);
            setVerifyEvidence(null);
          }}
          offerId={verifyEvidence?.offerId ?? ""}
          evidence={verifyEvidence?.evidence}
          onSuccess={fetchReviews}
        />
      )}
    </div>
  );
}
