"use client";

export const dynamic = "force-dynamic";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiRequest } from "@/lib/api";
import {
  CommercialOfferResponse,
  CatalogAuditResponse,
  OfferPriceHistoryResponse,
  OfferEvidenceResponse,
} from "@/types/offer";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { ErrorBanner } from "@/components/common/ErrorBanner";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PriceFormatter } from "@/components/common/PriceFormatter";
import { EvidenceCard } from "@/components/catalog/EvidenceCard";
import { VerifyEvidenceDialog } from "@/components/catalog/VerifyEvidenceDialog";
import { PublishDialog } from "@/components/catalog/PublishDialog";
import { ArchiveDialog } from "@/components/catalog/ArchiveDialog";
import {
  ShieldAlert,
  Archive,
  Rocket,
  Plus,
  RefreshCw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatDate, formatDateTime, isFreshnessStale, truncate } from "@/lib/utils";
export default function OfferDetailPage() {
  const params = useParams<{ offerId: string }>();
  const offerId = params.offerId;
  const router = useRouter();

  const [offer, setOffer] = useState<CommercialOfferResponse | null>(null);
  const [priceHistory, setPriceHistory] = useState<OfferPriceHistoryResponse[]>([]);
  const [auditHistory, setAuditHistory] = useState<CatalogAuditResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [verifyEvidence, setVerifyEvidence] = useState<OfferEvidenceResponse | null>(null);

  const fetchOffer = useCallback(async () => {
    if (!offerId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await apiRequest<CommercialOfferResponse>(
        `/api/admin/catalog/offers/${offerId}`,
      );
      setOffer(data);
    } catch (err) {
      const e = err as { status?: number; message?: string };
      if (e.status === 404) {
        setError("Offre introuvable.");
      } else if (e.status === 401 || e.status === 403) {
        router.push("/login");
      } else {
        setError(e.message || "Echec du chargement de l'offre.");
      }
    } finally {
      setLoading(false);
    }
  }, [offerId, router]);

  const fetchHistories = useCallback(async () => {
    if (!offerId) return;
    try {
      const [ph, ah] = await Promise.all([
        apiRequest<OfferPriceHistoryResponse[]>(
          `/api/admin/catalog/offers/${offerId}/price-history`,
        ),
        apiRequest<CatalogAuditResponse[]>(
          `/api/admin/catalog/offers/${offerId}/audit`,
        ),
      ]);
      setPriceHistory(ph || []);
      setAuditHistory(ah || []);
    } catch {
      // Histories are complementary; failure is not blocking
    }
  }, [offerId]);

  useEffect(() => {
    async function load() {
      await fetchOffer();
      await fetchHistories();
    }
    load();
  }, [fetchOffer, fetchHistories]);

  const refresh = useCallback(() => {
    fetchOffer();
    fetchHistories();
  }, [fetchOffer, fetchHistories]);

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <LoadingSpinner size={32} />
      </div>
    );
  }

  if (error || !offer) {
    return (
      <div className="rounded-lg border border-zinc-200 bg-white p-8 text-center">
        <ShieldAlert size={32} className="mx-auto text-red-400" />
        <h3 className="mt-2 text-sm font-medium text-zinc-900">
          {error || "Offre introuvable"}
        </h3>
        <Button onClick={refresh} className="mt-4">
          Actualiser
        </Button>
      </div>
    );
  }

  const pricing = offer.pricing;
  const provider = offer.provider;
  const latestEvidence = offer.evidence?.[0];

  const canPublish =
    offer.publicationStatus === "DRAFT" ||
    offer.publicationStatus === "STALE" ||
    offer.publicationStatus === "EXPIRED";
  const isPublished = offer.publicationStatus === "VERIFIED";

  const missingFields: string[] = [];
  if (!offer.characteristics) missingFields.push("Caractéristiques");
  if (!offer.eligibilityConditions) missingFields.push("Conditions d'éligibilité");
  if (!pricing || pricing.amountMinor == null) missingFields.push("Tarif standard");
  if (pricing && pricing.dataUnknown) missingFields.push("Données tarifaires complètes");
  if (!latestEvidence || latestEvidence.verificationStatus !== "VERIFIED") {
    missingFields.push("Preuve OFFER vérifiée");
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">{offer.commercialName}</h1>
          <p className="mt-1 text-sm text-zinc-500">
            ID {offer.id} • Secteur {offer.sector} • Territoire {offer.territory ?? "Non défini"}
          </p>
        </div>
        <div className="flex flex-col sm:flex-row gap-2">
          <StatusBadge status={offer.publicationStatus} />
          {canPublish && (
            <Button onClick={() => setPublishOpen(true)}>
              <Rocket size={16} className="mr-2" /> Publier
            </Button>
          )}
          {!isPublished && (
            <Button variant="outline" onClick={() => setArchiveOpen(true)}>
              <Archive size={16} className="mr-2" /> Archiver
            </Button>
          )}
          <Button variant="outline" onClick={refresh}>
            <RefreshCw size={16} className="mr-2" /> Actualiser
          </Button>
        </div>
      </div>

      {error && (
        <ErrorBanner
          title="Erreur"
          message={error}
          onDismiss={() => setError(null)}
          retry={refresh}
          retryLabel="Réessayer"
        />
      )}

      {/* Informations générales */}
      <section className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Informations générales
            </h2>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="col-span-2">
                <dt className="text-xs text-zinc-500">Nom commercial</dt>
                <dd className="text-sm text-zinc-900">{offer.commercialName}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-zinc-500">Secteur</dt>
                <dd className="text-sm text-zinc-900 capitalize">{offer.sector}</dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Territoire</dt>
                <dd className="text-sm text-zinc-900">{offer.territory ?? "Non renseigné"}</dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Engagement</dt>
                <dd className="text-sm text-zinc-900">{offer.commitment ?? "Non renseigné"}</dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Validité début</dt>
                <dd className="text-sm text-zinc-900">{formatDate(offer.validFrom)}</dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Validité fin</dt>
                <dd className="text-sm text-zinc-900">{formatDate(offer.validUntil)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-zinc-500">Statut de publication</dt>
                <dd className="text-sm text-zinc-900">
                  <StatusBadge status={offer.publicationStatus} />
                </dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-zinc-500">Crée le</dt>
                <dd className="text-sm text-zinc-900">{formatDateTime(offer.createdAt)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-zinc-500">Dernière modification</dt>
                <dd className="text-sm text-zinc-900">{formatDateTime(offer.updatedAt)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-zinc-500">Version</dt>
                <dd className="text-sm text-zinc-900 font-mono">{offer.version}</dd>
              </div>
            </dl>
          </div>

          {/* Caractéristiques */}
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Caractéristiques
            </h2>
            <div className="whitespace-pre-wrap break-words text-sm text-zinc-700">
              {offer.characteristics ? (
                <>{offer.characteristics}</>
              ) : (
                <span className="text-zinc-400 italic">Non renseigné</span>
              )}
            </div>
            {offer.characteristics ? null : (
              <p className="mt-2 text-xs text-zinc-500">
                Champ manquant : les caractéristiques de l'offre n'ont pas été renseignées.
              </p>
            )}
          </div>

          {/* Conditions d'éligibilité */}
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Conditions d'éligibilité
            </h2>
            <div className="whitespace-pre-wrap break-words text-sm text-zinc-700">
              {offer.eligibilityConditions ? (
                <>{offer.eligibilityConditions}</>
              ) : (
                <span className="text-zinc-400 italic">Non renseigné</span>
              )}
            </div>
            {offer.eligibilityConditions ? null : (
              <p className="mt-2 text-xs text-zinc-500">
                Champ manquant : les conditions d'éligibilité n'ont pas été renseignées.
              </p>
            )}
          </div>
        </div>

        <div className="lg:col-span-1 space-y-6">
          {/* Fournisseur */}
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Fournisseur
            </h2>
            <dl className="space-y-3">
              <div>
                <dt className="text-xs text-zinc-500">Nom</dt>
                <dd className="text-sm font-medium text-zinc-900">
                  {provider?.name ?? "Inconnu"}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Secteur</dt>
                <dd className="text-sm text-zinc-900 capitalize">
                  {provider?.sector ?? "-"}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Statut</dt>
                <dd className="text-sm text-zinc-900">
                  <StatusBadge status={provider?.status ?? "INACTIVE"} />
                </dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Pays</dt>
                <dd className="text-sm text-zinc-900">{provider?.country ?? "-"}</dd>
              </div>
              {provider?.officialWebsite && (
                <div>
                  <dt className="text-xs text-zinc-500">Site officiel</dt>
                  <a
                    href={provider.officialWebsite}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-sm text-blue-600 underline break-all"
                  >
                    {truncate(provider.officialWebsite, 60)}
                  </a>
                </div>
              )}
              <div>
                <dt className="text-xs text-zinc-500">Crée le</dt>
                <dd className="text-sm text-zinc-900">
                  {formatDateTime(provider?.createdAt)}
                </dd>
              </div>
            </dl>
          </div>

          {/* Tarification */}
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Tarification
            </h2>
            {pricing ? (
              <dl className="space-y-3">
                <div>
                  <dt className="text-xs text-zinc-500">Prix standard</dt>
                  <dd className="text-sm font-medium text-zinc-900">
                    <PriceFormatter
                      amountMinor={pricing.amountMinor}
                      currency={pricing.currency ?? "EUR"}
                      billingPeriod={pricing.billingPeriod ?? undefined}
                    />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Frais d'activation</dt>
                  <dd className="text-sm text-zinc-900">
                    <PriceFormatter
                      amountMinor={pricing.activationFeeMinor ?? null}
                      currency={pricing.currency ?? "EUR"}
                    />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Autres frais</dt>
                  <dd className="text-sm text-zinc-900">
                    {pricing.otherFees || "Non renseigné"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Taxes incluses</dt>
                  <dd className="text-sm text-zinc-900">
                    {pricing.taxesIncluded ? "Oui" : pricing.taxesIncluded === false ? "Non" : "Non renseigné"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Données inconnues</dt>
                  <dd className={`text-sm font-medium ${pricing.dataUnknown ? "text-red-600" : "text-zinc-900"}`}>
                    {pricing.dataUnknown ? "Oui - tarif non vérifié" : "Non"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Crée le</dt>
                  <dd className="text-sm text-zinc-900">{formatDateTime(pricing.createdAt)}</dd>
                </div>
              </dl>
            ) : (
              <div className="text-sm text-zinc-500 italic">Aucune tarification renseignée</div>
            )}
          </div>

          {/* Promotions */}
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Promotions
            </h2>
            {pricing && pricing.promotionPriceType ? (
              <dl className="space-y-3">
                <div>
                  <dt className="text-xs text-zinc-500">Type de promotion</dt>
                  <dd className="text-sm text-zinc-900 capitalize">
                    {pricing.promotionPriceType}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Pourcentage</dt>
                  <dd className="text-sm text-zinc-900">
                    {pricing.promotionPercent != null ? `${pricing.promotionPercent}%` : "Non renseigné"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Durée (mois)</dt>
                  <dd className="text-sm text-zinc-900">
                    {pricing.promotionDurationMonths ?? "Non renseigné"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Prix après promotion</dt>
                  <dd className="text-sm text-zinc-900">
                    <PriceFormatter amountMinor={pricing.priceAfterPromotionMinor ?? null} />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Validité promotion</dt>
                  <dd className="text-sm text-zinc-900">
                    {formatDate(pricing.promotionValidFrom)} à {formatDate(pricing.promotionValidUntil)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-zinc-500">Promotion vérifiée</dt>
                  <dd className={`text-sm font-medium ${pricing.promotionVerified ? "text-green-700" : "text-amber-700"}`}>
                    {pricing.promotionVerified ? "Oui" : "Non"}
                  </dd>
                </div>
              </dl>
            ) : (
              <div className="text-sm text-zinc-500 italic">Aucune promotion configurée</div>
            )}
          </div>

          {/* Statut de publication */}
          <div className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
              Statut de publication
            </h2>
            <dl className="space-y-3">
              <div>
                <dt className="text-xs text-zinc-500">Statut actuel</dt>
                <dd className="text-sm text-zinc-900">
                  <StatusBadge status={offer.publicationStatus} />
                </dd>
              </div>
              <div className="border-t border-zinc-200 pt-3">
                <dt className="text-xs text-zinc-500">Champs manquants</dt>
                <dd className="text-sm text-zinc-900">
                  {missingFields.length > 0 ? (
                    <ul className="text-sm text-red-600 list-disc pl-4 space-y-1">
                      {missingFields.map((f) => (
                        <li key={f}>{f}</li>
                      ))}
                    </ul>
                  ) : (
                    <span className="text-green-700">Aucun champ critique manquant</span>
                  )}
                </dd>
              </div>
              <div className="border-t border-zinc-200 pt-3">
                <dt className="text-xs text-zinc-500">Fraîcheur de la preuve</dt>
                <dd className="text-sm text-zinc-900">
                  {latestEvidence?.verifiedAt
                    ? isFreshnessStale(latestEvidence.verifiedAt)
                      ? "Preuve non fraîche (plus de 30 jours)"
                      : "Preuve fraîche"
                    : "Aucune date de vérification"}
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </section>

      {/* Preuves */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider">
            Preuves et sources officielles
          </h2>
          {offer.evidence?.some((e) => e.verificationStatus === "PENDING") && (
            <Button size="sm" onClick={() => {
              const pending = offer.evidence?.find((e) => e.verificationStatus === "PENDING");
              if (pending) setVerifyEvidence(pending);
              setVerifyOpen(true);
            }}>
              <Plus size={16} className="mr-2" /> Valider une preuve
            </Button>
          )}
        </div>

        {offer.evidence && offer.evidence.length > 0 ? (
          <div className="space-y-3">
            {offer.evidence.map((ev) => (
              <EvidenceCard
                key={ev.id}
                evidence={ev}
                onVerify={offer.evidence.some((e) => e.verificationStatus === "PENDING")
                  ? (e) => {
                      setVerifyEvidence(e);
                      setVerifyOpen(true);
                    }
                  : undefined}
              />
            ))}
          </div>
        ) : (
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-6 text-center">
            <ShieldAlert size={24} className="mx-auto text-zinc-400" />
            <h3 className="mt-2 text-sm font-medium text-zinc-900">Aucune preuve renseignée</h3>
            <p className="text-sm text-zinc-500">
              Aucune source officielle n'a été ajoutée pour cette offre.
            </p>
          </div>
        )}
      </section>

      {/* Historique tarifaire */}
      <section className="space-y-4">
        <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider">
          Historique tarifaire
        </h2>
        {priceHistory.length > 0 ? (
          <div className="rounded-lg border border-zinc-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-zinc-50 border-b border-zinc-200">
                <tr>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Date</th>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Ancien prix</th>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Nouveau prix</th>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Motif</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200">
                {priceHistory.map((h) => (
                  <tr key={h.id} className="p-3">
                    <td className="text-zinc-900">{formatDateTime(h.changedAt)}</td>
                    <td className="text-zinc-900">
                      {h.oldAmountMinor != null ? <PriceFormatter amountMinor={h.oldAmountMinor} /> : "—"}
                    </td>
                    <td className="text-zinc-900">
                      {h.newAmountMinor != null ? <PriceFormatter amountMinor={h.newAmountMinor} /> : "—"}
                    </td>
                    <td className="text-sm text-zinc-500">{truncate(h.reason, 40)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-zinc-500 italic">Aucun historique tarifaire.</p>
        )}
      </section>

      {/* Audit */}
      <section className="space-y-4">
        <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider">
          Journal d'audit administratif
        </h2>
        {auditHistory.length > 0 ? (
          <div className="rounded-lg border border-zinc-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-zinc-50 border-b border-zinc-200">
                <tr>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Date</th>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Action</th>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Ressource</th>
                  <th className="p-3 text-left text-xs font-semibold text-zinc-600">Détails</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200">
                {auditHistory.map((a) => (
                  <tr key={a.id} className="p-3">
                    <td className="text-zinc-900">{formatDateTime(a.occurredAt)}</td>
                    <td className="text-sm text-zinc-900">{a.action}</td>
                    <td className="text-sm text-zinc-500">{a.resourceType}</td>
                    <td className="text-sm text-zinc-500">
                      {a.details ? JSON.stringify(a.details).slice(0, 60) : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-zinc-500 italic">Aucune entrée d'audit.</p>
        )}
      </section>

      {/* Dialogs */}
      {verifyOpen && verifyEvidence && (
        <VerifyEvidenceDialog
          open={verifyOpen}
          onClose={() => { setVerifyOpen(false); setVerifyEvidence(null); }}
          offerId={offerId}
          evidence={verifyEvidence}
          onSuccess={refresh}
        />
      )}
      {publishOpen && (
        <PublishDialog
          open={publishOpen}
          onClose={() => setPublishOpen(false)}
          offerId={offerId}
          offerName={offer.commercialName}
          onSuccess={refresh}
        />
      )}
      {archiveOpen && (
        <ArchiveDialog
          open={archiveOpen}
          onClose={() => setArchiveOpen(false)}
          offerId={offerId}
          offerName={offer.commercialName}
          onSuccess={refresh}
        />
      )}
    </div>
  );
}
