"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { buttonClass } from "./button-class";

/** Boîte de confirmation accessible basée sur <dialog> (focus piégé, Échap pour fermer). */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  tone = "primary",
  busy,
  confirmDisabled,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  tone?: "primary" | "danger";
  busy?: boolean;
  confirmDisabled?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      className="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-lg border border-border bg-surface p-0 text-foreground shadow-xl backdrop:bg-foreground/30"
    >
      <div className="p-5">
        <h2 className="text-base font-semibold">{title}</h2>
        <div className="mt-3 space-y-3 text-muted">{children}</div>
      </div>
      <div className="flex justify-end gap-2 border-t border-border px-5 py-3">
        <button type="button" className={buttonClass("secondary")} onClick={onClose} disabled={busy}>
          Annuler
        </button>
        <button
          type="button"
          className={buttonClass(tone)}
          onClick={onConfirm}
          disabled={busy || confirmDisabled}
          aria-busy={busy}
        >
          {busy ? "Envoi…" : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
