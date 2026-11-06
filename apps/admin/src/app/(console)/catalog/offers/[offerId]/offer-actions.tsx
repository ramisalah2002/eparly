"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { buttonClass } from "@/components/button-class";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { bffMutation } from "@/lib/client/bff";
import type { OfferStatus } from "@/lib/types";

type Action = "publish" | "archive";

/**
 * Publication et archivage. Le statut affiché ne change qu'après la réponse du serveur
 * (router.refresh) : aucun succès n'est supposé localement.
 */
export function OfferActions({
  offerId,
  status,
  blockingItems,
  publicationEnabled,
}: {
  offerId: string;
  status: OfferStatus;
  blockingItems: number;
  publicationEnabled: boolean;
}) {
  const router = useRouter();
  const [action, setAction] = useState<Action | null>(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "ok" | "error"; text: string } | null>(null);

  function open(next: Action) {
    setAction(next);
    setAcknowledged(false);
    setFeedback(null);
  }

  async function submit() {
    if (!action || busy) return;
    setBusy(true);
    const result = await bffMutation(`/api/admin/catalog/offers/${offerId}/${action}`, "POST");
    setBusy(false);
    setAction(null);
    if (!result.ok) {
      setFeedback({ tone: "error", text: result.message });
      return;
    }
    setFeedback({
      tone: "ok",
      text: action === "publish" ? "Le serveur a accepté la publication." : "Le serveur a archivé l'offre.",
    });
    router.refresh();
  }

  const archived = status === "ARCHIVED";

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className={buttonClass("primary")}
          onClick={() => open("publish")}
          disabled={archived || !publicationEnabled}
          title={!publicationEnabled ? "Publication désactivée sur cet environnement" : undefined}
        >
          Demander la publication
        </button>
        <button type="button" className={buttonClass("secondary")} onClick={() => open("archive")} disabled={archived}>
          Archiver
        </button>
      </div>
      {!publicationEnabled && (
        <p className="text-xs text-muted">
          Publication désactivée sur cet environnement (variable EPARLY_ADMIN_ALLOW_PUBLISH). Aucune publication pendant
          le développement.
        </p>
      )}
      {feedback && (
        <p
          role={feedback.tone === "error" ? "alert" : "status"}
          className={`rounded-xl px-3 py-2 ${feedback.tone === "error" ? "bg-danger-soft text-danger" : "bg-ok-soft text-ok"}`}
        >
          {feedback.text}
        </p>
      )}

      <ConfirmDialog
        open={action === "publish"}
        title="Demander la publication"
        confirmLabel="Envoyer la demande"
        busy={busy}
        confirmDisabled={!acknowledged}
        onConfirm={submit}
        onClose={() => !busy && setAction(null)}
      >
        <p>
          Le backend contrôle le fournisseur, le tarif, la période de validité et la fraîcheur des preuves. En cas de refus,
          l&apos;offre reste inchangée et le motif est affiché.
        </p>
        {blockingItems > 0 && (
          <p className="rounded-xl bg-warn-soft px-3 py-2 text-warn">
            {blockingItems} blocage{blockingItems > 1 ? "s" : ""} détecté{blockingItems > 1 ? "s" : ""} : un refus est probable.
          </p>
        )}
        <label className="flex items-start gap-2 text-foreground">
          <input type="checkbox" checked={acknowledged} onChange={(e) => setAcknowledged(e.target.checked)} className="mt-1" />
          <span>
            Je confirme que les droits de réutilisation de cette offre ont été établis par une décision documentée, hors
            back-office.
          </span>
        </label>
      </ConfirmDialog>

      <ConfirmDialog
        open={action === "archive"}
        title="Archiver l'offre"
        confirmLabel="Archiver"
        tone="danger"
        busy={busy}
        onConfirm={submit}
        onClose={() => !busy && setAction(null)}
      >
        <p>
          L&apos;offre ne sera plus recommandée. L&apos;archivage est logique : historique, preuves et audit sont conservés.
        </p>
      </ConfirmDialog>
    </div>
  );
}
