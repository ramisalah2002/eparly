"use client";

type StatusVariant =
  | "DRAFT"
  | "VERIFIED"
  | "STALE"
  | "EXPIRED"
  | "ARCHIVED"
  | "PENDING"
  | "INVALID";

interface StatusBadgeProps {
  status: string;
}

function variantClass(v: StatusVariant): string {
  switch (v) {
    case "VERIFIED":
      return "bg-green-100 text-green-800 border-green-200";
    case "DRAFT":
      return "bg-amber-100 text-amber-800 border-amber-200";
    case "STALE":
      return "bg-orange-100 text-orange-800 border-orange-200";
    case "EXPIRED":
      return "bg-red-100 text-red-800 border-red-200";
    case "ARCHIVED":
      return "bg-zinc-100 text-zinc-800 border-zinc-200";
    case "PENDING":
      return "bg-blue-100 text-blue-800 border-blue-200";
    case "INVALID":
      return "bg-red-100 text-red-800 border-red-200";
    default:
      return "bg-zinc-100 text-zinc-500 border-zinc-200";
  }
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const v = status as StatusVariant;
  const label = {
    DRAFT: "Brouillon",
    VERIFIED: "Vérifié",
    STALE: "Obsolète",
    EXPIRED: "Expiré",
    ARCHIVED: "Archivé",
    PENDING: "En attente",
    INVALID: "Invalide",
  }[v] ?? status;

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${variantClass(v)}`}
    >
      {label}
    </span>
  );
}
