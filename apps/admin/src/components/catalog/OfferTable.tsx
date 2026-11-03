"use client";

import {
  CommercialOfferResponse,
} from "@/types/offer";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PriceFormatter } from "@/components/common/PriceFormatter";
import { formatDate } from "@/lib/utils";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MoreHorizontal, Clock, ExternalLink } from "lucide-react";

export interface OfferRowProps {
  offer: CommercialOfferResponse;
}

export function OfferRow({ offer }: OfferRowProps) {
  const router = useRouter();
  const provider = offer.provider;
  const pricing = offer.pricing;
  const evidence = offer.evidence?.[0];

  const hasEvidence = evidence?.verificationStatus === "VERIFIED";
  const evidencePending = evidence?.verificationStatus === "PENDING";
  const evidenceExpired = evidence?.verificationStatus === "EXPIRED";
  const evidenceInvalid = evidence?.verificationStatus === "INVALID";

  let evidenceLabel = "Aucune preuve";
  let evidenceClass = "bg-zinc-100 text-zinc-500 border-zinc-200";
  if (hasEvidence) {
    evidenceLabel = "Preuve vérifiée";
    evidenceClass = "bg-green-100 text-green-800 border-green-200";
  } else if (evidencePending) {
    evidenceLabel = "Preuve en attente";
    evidenceClass = "bg-blue-100 text-blue-800 border-blue-200";
  } else if (evidenceExpired) {
    evidenceLabel = "Preuve expirée";
    evidenceClass = "bg-red-100 text-red-800 border-red-200";
  } else if (evidenceInvalid) {
    evidenceLabel = "Preuve invalide";
    evidenceClass = "bg-red-100 text-red-800 border-red-200";
  }

  return (
    <tr
      className="border-b border-zinc-200 hover:bg-zinc-50/50 transition-colors"
      onClick={() => router.push(`/catalog/offers/${offer.id}`)}
      style={{ cursor: "pointer" }}
    >
      <td className="p-3 align-middle">
        <div className="text-sm font-medium text-zinc-900">{provider?.name ?? "Inconnu"}</div>
        <div className="text-xs text-zinc-500">{provider?.sector ?? "-"}</div>
      </td>
      <td className="p-3 align-middle">
        <div className="text-sm font-medium text-zinc-900">{offer.commercialName}</div>
      </td>
      <td className="p-3 align-middle">
        <span className="text-xs text-zinc-500">{offer.sector ?? "-"}</span>
      </td>
      <td className="p-3 align-middle">
        <PriceFormatter
          amountMinor={pricing?.amountMinor ?? null}
          currency={pricing?.currency ?? "EUR"}
          billingPeriod={pricing?.billingPeriod ?? undefined}
        />
      </td>
      <td className="p-3 align-middle">
        {pricing?.promotionPriceType && pricing?.promotionPercent != null ? (
          <span className="text-xs font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-100">
            -{pricing.promotionPercent}%
          </span>
        ) : (
          <span className="text-xs text-zinc-400">—</span>
        )}
      </td>
      <td className="p-3 align-middle">
        <StatusBadge status={offer.publicationStatus} />
      </td>
      <td className="p-3 align-middle">
        <span className="text-xs text-zinc-500">{formatDate(offer.updatedAt?.toString())}</span>
      </td>
      <td className="p-3 align-middle">
        <span
          className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${evidenceClass}`}
        >
          {evidenceLabel}
        </span>
      </td>
      <td className="p-3 align-middle">
        <div className="flex items-center gap-2">
          <Link
            href={`/catalog/offers/${offer.id}`}
            className="p-2 text-zinc-400 hover:text-zinc-600"
            title="Détails"
          >
            <MoreHorizontal size={16} />
          </Link>
          {provider?.officialWebsite && (
            <a
              href={provider.officialWebsite}
              target="_blank"
              rel="noopener noreferrer"
              className="p-2 text-zinc-400 hover:text-blue-600"
              title="Site officiel"
            >
              <ExternalLink size={16} />
            </a>
          )}
        </div>
      </td>
    </tr>
  );
}

interface OfferTableProps {
  offers: CommercialOfferResponse[];
  loading: boolean;
  empty: boolean;
}

export function OfferTable({ offers, loading, empty }: OfferTableProps) {
  if (loading) {
    return (
      <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-4">
        <div className="flex flex-col gap-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="flex gap-4">
              <div className="w-1/3 h-4 bg-zinc-200 rounded animate-pulse" />
              <div className="w-1/4 h-4 bg-zinc-200 rounded animate-pulse" />
              <div className="w-1/6 h-4 bg-zinc-200 rounded animate-pulse" />
              <div className="w-1/6 h-4 bg-zinc-200 rounded animate-pulse" />
              <div className="w-1/5 h-4 bg-zinc-200 rounded animate-pulse" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (empty) {
    return (
      <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-8 text-center">
        <Clock size={32} className="mx-auto text-zinc-400" />
        <h3 className="mt-2 text-sm font-medium text-zinc-900">Aucune offre trouvée</h3>
        <p className="mt-1 text-sm text-zinc-500">
          Aucune offre commerciale ne corresponde aux filtres sélectionnés.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-zinc-200 overflow-hidden">
      <table className="w-full text-sm text-left">
        <thead className="bg-zinc-50 border-b border-zinc-200">
          <tr>
            <th className="p-3 font-semibold text-zinc-600">Fournisseur</th>
            <th className="p-3 font-semibold text-zinc-600">Nom de l'offre</th>
            <th className="p-3 font-semibold text-zinc-600">Secteur</th>
            <th className="p-3 font-semibold text-zinc-600">Prix standard</th>
            <th className="p-3 font-semibold text-zinc-600">Promotion</th>
            <th className="p-3 font-semibold text-zinc-600">Statut</th>
            <th className="p-3 font-semibold text-zinc-600">Dernière modif.</th>
            <th className="p-3 font-semibold text-zinc-600">Preuves</th>
            <th className="p-3 font-semibold text-zinc-600">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-200">
          {offers.map((offer) => (
            <OfferRow key={offer.id} offer={offer} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
