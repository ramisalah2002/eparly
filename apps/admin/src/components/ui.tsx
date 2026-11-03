import Link from "next/link";
import type { ReactNode } from "react";
import { EVIDENCE_STATUS_LABELS, OFFER_STATUS_LABELS } from "@/lib/format";
import type { CheckState } from "@/lib/checklist";
import type { EvidenceStatus, OfferStatus } from "@/lib/types";

type Tone = "neutral" | "lavender" | "beige" | "ok" | "warn" | "danger";

const TONES: Record<Tone, string> = {
  neutral: "bg-background text-muted border-border",
  lavender: "bg-lavender-soft text-lavender-strong border-lavender/40",
  beige: "bg-beige-soft text-[#6b5435] border-beige/60",
  ok: "bg-ok-soft text-ok border-ok/20",
  warn: "bg-warn-soft text-warn border-warn/20",
  danger: "bg-danger-soft text-danger border-danger/20",
};

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium ${TONES[tone]}`}>
      {children}
    </span>
  );
}

const OFFER_TONES: Record<OfferStatus, Tone> = {
  DRAFT: "beige",
  VERIFIED: "ok",
  STALE: "warn",
  EXPIRED: "neutral",
  ARCHIVED: "neutral",
};

export function OfferStatusBadge({ status }: { status: OfferStatus }) {
  return <Badge tone={OFFER_TONES[status]}>{OFFER_STATUS_LABELS[status]}</Badge>;
}

const EVIDENCE_TONES: Record<EvidenceStatus, Tone> = {
  PENDING: "warn",
  VERIFIED: "ok",
  EXPIRED: "neutral",
  INVALID: "danger",
};

/** Statut de preuve, volontairement distinct visuellement du statut d'offre. */
export function EvidenceStatusBadge({ status }: { status: EvidenceStatus }) {
  return <Badge tone={EVIDENCE_TONES[status]}>Preuve · {EVIDENCE_STATUS_LABELS[status]}</Badge>;
}

const CHECK_TONES: Record<CheckState, Tone> = { ok: "ok", blocking: "danger", unknown: "warn" };
const CHECK_LABELS: Record<CheckState, string> = { ok: "OK", blocking: "Bloquant", unknown: "À confirmer" };

export function CheckBadge({ state }: { state: CheckState }) {
  return <Badge tone={CHECK_TONES[state]}>{CHECK_LABELS[state]}</Badge>;
}

export function Card({
  id,
  title,
  actions,
  children,
}: {
  id?: string;
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-4 rounded-lg border border-border bg-surface">
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          {title && <h2 className="text-sm font-semibold">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-muted">{description}</p>}
      </div>
      {actions}
    </div>
  );
}

export function Notice({ tone = "warn", title, children }: { tone?: "warn" | "danger" | "lavender" | "ok"; title?: string; children: ReactNode }) {
  return (
    <div role={tone === "danger" ? "alert" : "note"} className={`rounded-lg border px-4 py-3 ${TONES[tone]}`}>
      {title && <p className="font-semibold">{title}</p>}
      <div className={title ? "mt-1" : ""}>{children}</div>
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-border bg-surface px-6 py-10 text-center">
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 text-muted">{children}</div>}
    </div>
  );
}

export function Skeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-2" aria-busy="true" aria-label="Chargement">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-9 animate-pulse rounded bg-border/60" />
      ))}
    </div>
  );
}

export function DefinitionList({ items }: { items: [ReactNode, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-[minmax(10rem,auto)_1fr]">
      {items.map(([term, value], index) => (
        <div key={index} className="contents">
          <dt className="text-muted">{term}</dt>
          <dd className="break-words">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function TextLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="font-medium text-lavender-strong underline-offset-2 hover:underline">
      {children}
    </Link>
  );
}
