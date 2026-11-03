"use client";

import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { buttonClass } from "@/components/button-class";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { bffMutation } from "@/lib/client/bff";
import type { EvidenceStatus } from "@/lib/types";

type Decision = Extract<EvidenceStatus, "VERIFIED" | "INVALID">;

const MIN_SUMMARY = 10;

/**
 * Décision humaine sur une preuve. L'API n'effectue aucun contrôle externe : l'administrateur
 * doit avoir réellement consulté la source, ce que la case à cocher rend explicite.
 */
export function EvidenceReview({
  offerId,
  evidenceId,
  expectedVersion,
  status,
  officialUrl,
}: {
  offerId: string;
  evidenceId: string;
  expectedVersion: number;
  status: EvidenceStatus;
  officialUrl: string | null;
}) {
  const router = useRouter();
  const ids = useId();
  const [decision, setDecision] = useState<Decision | null>(null);
  const [summary, setSummary] = useState("");
  const [consulted, setConsulted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const allowed: Decision[] = status === "PENDING" ? ["VERIFIED", "INVALID"] : status === "VERIFIED" ? ["INVALID"] : [];
  if (allowed.length === 0) return null;

  function open(next: Decision) {
    setDecision(next);
    setSummary("");
    setConsulted(false);
    setError(null);
  }

  async function submit() {
    if (!decision || busy) return;
    setBusy(true);
    setError(null);
    const result = await bffMutation(
      `/api/admin/catalog/offers/${offerId}/evidences/${evidenceId}/verification`,
      "PATCH",
      { expectedVersion, status: decision, summary: summary.trim() },
    );
    setBusy(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setDecision(null);
    router.refresh();
  }

  const summaryValid = summary.trim().length >= MIN_SUMMARY;
  const needsConsultation = decision === "VERIFIED";

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {allowed.includes("VERIFIED") && (
          <button type="button" className={buttonClass("primary")} onClick={() => open("VERIFIED")}>
            Valider la preuve
          </button>
        )}
        <button type="button" className={buttonClass("secondary")} onClick={() => open("INVALID")}>
          Invalider
        </button>
      </div>
      <ConfirmDialog
        open={decision !== null}
        title={decision === "VERIFIED" ? "Valider cette preuve" : "Invalider cette preuve"}
        confirmLabel={decision === "VERIFIED" ? "Enregistrer la validation" : "Enregistrer l'invalidation"}
        tone={decision === "INVALID" ? "danger" : "primary"}
        busy={busy}
        confirmDisabled={!summaryValid || (needsConsultation && !consulted)}
        onConfirm={submit}
        onClose={() => !busy && setDecision(null)}
      >
        {decision === "VERIFIED" && (
          <p>
            La validation enregistre votre identité et l&apos;heure serveur. Elle ne vaut pas établissement des droits de
            réutilisation. Une preuve sur page d&apos;accueil ou une date approximative ne doit pas être validée par défaut.
          </p>
        )}
        {officialUrl && (
          <p>
            Source :{" "}
            <a href={officialUrl} target="_blank" rel="noopener noreferrer" className="break-all text-lavender-strong underline">
              {officialUrl}
            </a>
          </p>
        )}
        <div>
          <label htmlFor={`${ids}-summary`} className="block font-medium text-foreground">
            Justification (obligatoire)
          </label>
          <textarea
            id={`${ids}-summary`}
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            rows={4}
            maxLength={2000}
            aria-describedby={`${ids}-summary-hint`}
            className="mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-foreground"
          />
          <p id={`${ids}-summary-hint`} className="mt-1 text-xs">
            Ce qui a été constaté sur la source : prix, conditions, date de consultation. {MIN_SUMMARY} caractères minimum.
          </p>
        </div>
        {needsConsultation && (
          <label className="flex items-start gap-2 text-foreground">
            <input type="checkbox" checked={consulted} onChange={(e) => setConsulted(e.target.checked)} className="mt-1" />
            <span>J&apos;ai consulté la source officielle et vérifié qu&apos;elle correspond au tarif et aux conditions actuels.</span>
          </label>
        )}
        {error && (
          <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-danger">
            {error}
          </p>
        )}
      </ConfirmDialog>
    </>
  );
}
