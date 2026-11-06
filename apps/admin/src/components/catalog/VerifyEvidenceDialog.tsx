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
} from "@/components/ui/dialog";
import { OfferEvidenceResponse } from "@/types/offer";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { apiRequest } from "@/lib/api";
import { HttpError } from "@/lib/api";

const verifySchema = z.object({
  // expectedVersion est rempli automatiquement depuis la réponse backend
  // (version de la preuve) pour Éviter que l'administrateur ne doive la deviner.
  expectedVersion: z.string().min(1, "Version requise"),
  status: z.enum(["VERIFIED", "INVALID"]),
  summary: z.string().min(1, "Un résumé est requis").max(2000),
});

interface VerifyEvidenceDialogProps {
  open: boolean;
  onClose: () => void;
  offerId: string;
  evidence: OfferEvidenceResponse;
  onSuccess: () => void;
}

export function VerifyEvidenceDialog({
  open,
  onClose,
  offerId,
  evidence,
  onSuccess,
}: VerifyEvidenceDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // La version est rÉcupÉrÉe automatiquement depuis l'objet preuve (backend)
  // L'administrateur ne doit pas deviner cette valeur.
  const expectedVersion = String(evidence.version ?? "");

  const form = useForm<z.infer<typeof verifySchema>>({
    resolver: zodResolver(verifySchema),
    defaultValues: {
      expectedVersion,
      status: "VERIFIED",
      summary: "",
    },
  });

  const onSubmit = async (values: z.infer<typeof verifySchema>) => {
    setError(null);
    setSubmitting(true);
    try {
      await apiRequest(
        `/api/admin/catalog/offers/${offerId}/evidences/${evidence.id}/verification`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            expectedVersion: Number(values.expectedVersion),
            status: values.status,
            summary: values.summary,
          }),
        },
      );
      onSuccess();
      onClose();
    } catch (err) {
      setSubmitting(false);
      if (err instanceof HttpError) {
        if (err.status === 409) {
          setError(
            "Conflit de version : la preuve a ÉtÉ modifiÉe. Actualisez la page et rÉessayez.",
          );
        } else {
          setError(`${err.status} à ${err.message}`);
        }
      } else {
        setError("Erreur lors de la validation de la preuve.");
      }
    }
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Valider la preuve</DialogTitle>
          <DialogDescription>
            Examen de la preuve â€{evidence.nature}â€ (â€{evidence.officialUrl}â€).
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
          {error && (
            <div className="rounded-md bg-red-50 p-3 text-sm text-red-800 border border-red-200">
              {error}
            </div>
          )}
          <div className="space-y-2">
            <label className="block text-xs font-semibold text-zinc-600">
              Version de la preuve (expectedVersion)
            </label>
            <input
              {...form.register("expectedVersion")}
              type="text"
              readOnly
              defaultValue={expectedVersion}
              className="block w-full text-sm border border-zinc-300 rounded-lg p-2 bg-zinc-50 focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none"
              aria-describedby="expectedVersion-help"
            />
            <p id="expectedVersion-help" className="text-xs text-zinc-500">
              RÉcupÉrÉe automatiquement depuis la réponse backend. Ne pas modifier.
            </p>
            {form.formState.errors.expectedVersion && (
              <p className="text-xs text-red-600">{form.formState.errors.expectedVersion.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-zinc-600 uppercase tracking-wider">
              Action
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => form.setValue("status", "VERIFIED")}
                className={`px-3 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  form.getValues("status") === "VERIFIED"
                    ? "bg-blue-600 text-white border-blue-700"
                    : "bg-white text-zinc-700 border-zinc-300 hover:bg-zinc-50"
                }`}
              >
                Vérifié
              </button>
              <button
                type="button"
                onClick={() => form.setValue("status", "INVALID")}
                className={`px-3 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  form.getValues("status") === "INVALID"
                    ? "bg-red-600 text-white border-red-700"
                    : "bg-white text-zinc-700 border-zinc-300 hover:bg-zinc-50"
                }`}
              >
                Invalider
              </button>
            </div>
          </div>
          <div className="space-y-2">
            <label className="block text-xs font-semibold text-zinc-600">
              Résumé de vérification
            </label>
            <textarea
              {...form.register("summary")}
              rows={4}
              className="block w-full text-sm border border-zinc-300 rounded-lg p-2 focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none"
              placeholder="Décrivez ce qui a vérifié sur la source officielle..."
            />
            {form.formState.errors.summary && (
              <p className="text-xs text-red-600">{form.formState.errors.summary.message}</p>
            )}
          </div>
          <p className="text-xs text-zinc-500">
            La validation est un acte explicite de l'administrateur connecté. Aucune validation automatique n'est effectuée.
          </p>
        </form>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
            Annuler
          </Button>
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? "Validation en cours…" : "Valider la preuve"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
