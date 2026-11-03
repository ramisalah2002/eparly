"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogClose,
} from "@/components/ui/dialog";
import { Clock } from "lucide-react";
import { apiRequest, HttpError } from "@/lib/api";

interface ArchiveDialogProps {
  open: boolean;
  onClose: () => void;
  offerId: string;
  offerName: string;
  onSuccess: () => void;
}

export function ArchiveDialog({
  open,
  onClose,
  offerId,
  offerName,
  onSuccess,
}: ArchiveDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onArchive = async () => {
    setError(null);
    setSubmitting(true);
    try {
      await apiRequest(`/api/admin/catalog/offers/${offerId}/archive`, {
        method: "POST",
      });
      onSuccess();
      onClose();
    } catch (err) {
      setSubmitting(false);
      if (err instanceof HttpError) {
        setError(`${err.status} à ${err.message}`);
      } else {
        setError("Echec de l'archivage de l'offre.");
      }
    }
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogClose onClick={onClose} />
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>Archiver l'offre</DialogTitle>
          <DialogDescription>
            Vous allez archiver â€{offerName}â€.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800 border border-red-200">
              {error}
            </div>
          )}
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-4">
            <div className="flex items-start gap-3">
              <Clock size={18} className="mt-0.5 text-zinc-600" />
              <div className="text-sm text-zinc-700 leading-relaxed">
                L'offre sera désactivée logiquement (aucune suppression physique) et ne sera plus
                recommandée dans le catalogue public.
              </div>
            </div>
          </div>
          <p className="text-sm text-zinc-500">
            Cette opération est idempotente. Une offre archivée n'est plus exposée par l'API publique.
          </p>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
            Annuler
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={onArchive}
            disabled={submitting}
            className="w-full sm:w-auto"
          >
            {submitting ? "Archivage en cours…" : "Confirmer l'archivage"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
