"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { buttonClass } from "@/components/button-class";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { bffMutation } from "@/lib/client/bff";
import type { ProviderStatus } from "@/lib/types";

/** Active ou désactive un fournisseur ; le statut affiché vient toujours de la réponse serveur. */
export function ProviderStatusAction({
  providerId,
  status,
  version,
  publishedCount,
}: {
  providerId: string;
  status: ProviderStatus;
  version: number;
  publishedCount: number | null;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const next: ProviderStatus = status === "ACTIVE" ? "INACTIVE" : "ACTIVE";

  async function submit() {
    if (busy) return;
    setBusy(true);
    const result = await bffMutation(`/api/admin/catalog/providers/${providerId}`, "PATCH", {
      expectedVersion: version,
      status: next,
    });
    setBusy(false);
    setOpen(false);
    if (!result.ok) return setError(result.message);
    setError(null);
    router.refresh();
  }

  return (
    <>
      <button
        type="button"
        className={buttonClass(next === "INACTIVE" ? "danger" : "secondary")}
        onClick={() => {
          setError(null);
          setOpen(true);
        }}
      >
        {next === "INACTIVE" ? "Désactiver" : "Réactiver"}
      </button>
      {error && (
        <p role="alert" className="basis-full rounded-xl bg-danger-soft px-3 py-2 text-danger">
          {error}
        </p>
      )}
      <ConfirmDialog
        open={open}
        title={next === "INACTIVE" ? "Désactiver le fournisseur" : "Réactiver le fournisseur"}
        confirmLabel={next === "INACTIVE" ? "Désactiver" : "Réactiver"}
        tone={next === "INACTIVE" ? "danger" : undefined}
        busy={busy}
        onConfirm={submit}
        onClose={() => !busy && setOpen(false)}
      >
        <p>
          {next === "INACTIVE"
            ? "Le fournisseur reste dans le catalogue avec son historique, mais il est signalé comme inactif."
            : "Le fournisseur redevient actif."}
        </p>
        {!!publishedCount && (
          <p className="rounded-xl bg-warn-soft px-3 py-2 text-warn">
            {publishedCount} offre{publishedCount > 1 ? "s" : ""} publiée{publishedCount > 1 ? "s" : ""} repasser
            {publishedCount > 1 ? "ont" : "a"} « Périmée ».
          </p>
        )}
      </ConfirmDialog>
    </>
  );
}
