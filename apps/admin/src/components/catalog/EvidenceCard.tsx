"use client";

import { OfferEvidenceResponse } from "@/types/offer";
import { formatDateTime } from "@/lib/utils";
import { ExternalLink, ShieldCheck, Clock, AlertTriangle } from "lucide-react";

interface EvidenceCardProps {
  evidence: OfferEvidenceResponse;
  onVerify?: (evidence: OfferEvidenceResponse) => void;
}

export function EvidenceCard({ evidence, onVerify }: EvidenceCardProps) {
  const isPending = evidence.verificationStatus === "PENDING";
  const isVerified = evidence.verificationStatus === "VERIFIED";
  const isExpired = evidence.verificationStatus === "EXPIRED";
  const isInvalid = evidence.verificationStatus === "INVALID";

  const icon = isVerified ? (
    <ShieldCheck size={18} className="text-green-600" />
  ) : isPending ? (
    <Clock size={18} className="text-blue-600" />
  ) : isExpired || isInvalid ? (
    <AlertTriangle size={18} className="text-red-600" />
  ) : null;

  const statusColor = isVerified
    ? "text-green-600 border-green-100 bg-green-50"
    : isPending
      ? "text-blue-600 border-blue-100 bg-blue-50"
      : "text-red-600 border-red-100 bg-red-50";

  const freshness = isVerified && evidence.verifiedAt ? (
    <span className="text-xs text-zinc-500">
      Vérifié le {formatDateTime(evidence.verifiedAt)}
    </span>
  ) : isPending ? (
    <span className="text-xs text-blue-600">En attente de validation humaine</span>
  ) : null;

  return (
    <div className="border rounded-lg p-4">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div className="flex-1">
          <div className="flex items-start gap-3">
            {icon && <div className="flex-shrink-0 mt-0.5">{icon}</div>}
            <div className="flex-1">
              <div className="text-sm font-medium text-zinc-900">{evidence.nature}</div>
              <div className="text-xs text-zinc-500 truncate max-w-[300px]">{evidence.officialUrl}</div>
              {evidence.provenance && (
                <div className="mt-1 text-xs text-zinc-500">
                  Provenance : {evidence.provenance}
                </div>
              )}
              {evidence.evidenceScope && (
                <div className="mt-1 text-xs text-zinc-500 capitalize">
                  Scope : {evidence.evidenceScope.toLowerCase()}
                </div>
              )}
            </div>
          </div>
          <div className="mt-3 flex items-center gap-2">
            <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusColor}`}>
              {icon}
              {isVerified ? "Vérifié" : isPending ? "En attente" : isExpired ? "Expiré" : "Invalide"}
            </span>
            {freshness && <span className="ml-2">{freshness}</span>}
            {evidence.expiresAt && (
              <span className="ml-2 text-xs text-zinc-500">
                Expire le {formatDateTime(evidence.expiresAt)}
              </span>
            )}
          </div>
        </div>

        <div className="mt-3 sm:mt-0 flex gap-2">
          {evidence.officialUrl && (
            <a
              href={evidence.officialUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="px-3 py-1.5 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50 transition-colors"
            >
              <ExternalLink size={14} className="mr-1.5" /> Ouvrir la source
            </a>
          )}
          {isPending && onVerify && (
            <button
              type="button"
              onClick={() => onVerify(evidence)}
              className="px-3 py-1.5 text-xs font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
            >
              Valider la preuve
            </button>
          )}
          {isVerified && (
            <button
              type="button"
              disabled
              className="px-3 py-1.5 text-xs font-medium text-zinc-400 bg-zinc-100 border border-zinc-200 rounded-lg cursor-not-allowed"
            >
              Déjà vérifié
            </button>
          )}
        </div>
      </div>

      {evidence.verifiedSummary && (
        <div className="mt-3 pt-3 border-t border-zinc-200">
          <div className="text-xs font-semibold text-zinc-700">Résumé de vérification</div>
          <p className="mt-1 text-sm text-zinc-600 whitespace-pre-wrap break-words">
            {evidence.verifiedSummary}
          </p>
        </div>
      )}
    </div>
  );
}
