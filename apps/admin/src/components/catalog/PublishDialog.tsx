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
import { ShieldCheck, AlertTriangle } from "lucide-react";
import { apiRequest, HttpError } from "@/lib/api";

interface PublishDialogProps {
  open: boolean;
  onClose: () => void;
  offerId: string;
  offerName: string;
  onSuccess: () => void;
}

export function PublishDialog({
  open,
  onClose,
  offerId,
  offerName,
  onSuccess,
}: PublishDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onPublish = async () => {
    setError(null);
    setSubmitting(true);
    try {
      await apiRequest(`/api/admin/catalog/offers/${offerId}/publish`, {
        method: "POST",
      });
      onSuccess();
      onClose();
    } catch (err) {
      setSubmitting(false);
      if (err instanceof HttpError) {
        setError(`${err.status} à ${err.message}`);
      } else {
        setError("Echec de la publication de l'offre.");
      }
    }
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogClose onClick={onClose} />
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>Publier l'offre</DialogTitle>
          <DialogDescription>
            Vous allez publier â€{offerName}â€ dans le catalogue public Eparly.
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
              <AlertTriangle size={18} className="mt-0.5 text-orange-600" />
              <div className="text-sm text-zinc-700 leading-relaxed">
                Cette action rend l'offre visible dans le catalogue public Eparly si elle respecte les
                règles de disponibilité (prix fiable, preuve OFFER vérifiée et fraîche).
              </div>
            </div>
          </div>
          <p className="text-sm text-zinc-500">
            L'API Spring Boot vérifiera les permissions CATALOG_ADMIN, la preuve OFFER vérifiée et
            fraîche, le tarif et la disponibilité avant de publier. Une réponse 400 signifie qu'une
            condition métier manque, 409 qu'une version a changé.
          </p>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
            Annuler
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={onPublish}
            disabled={submitting}
            className="w-full sm:w-auto"
          >
            <ShieldCheck size={16} className="mr-2" />
            {submitting ? "Publication en cours…" : "Confirmer la publication"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
