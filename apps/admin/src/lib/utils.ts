import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatCurrencyEur(amountMinor: number | null | undefined): string {
  if (amountMinor === null || amountMinor === undefined) {
    return "Non renseigné";
  }
  const amount = amountMinor / 100;
  return new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "Non renseigné";
  try {
    return new Intl.DateTimeFormat("fr-FR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    }).format(new Date(value));
  } catch {
    return "Non renseigné";
  }
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "Non renseigné";
  try {
    return new Intl.DateTimeFormat("fr-FR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  } catch {
    return "Non renseigné";
  }
}

export function isFreshnessStale(verifiedAt: string | null | undefined, thresholdDays = 30): boolean {
  if (!verifiedAt) return true;
  try {
    const verified = new Date(verifiedAt).getTime();
    const now = Date.now();
    const ageDays = (now - verified) / (1000 * 60 * 60 * 24);
    return ageDays > thresholdDays;
  } catch {
    return true;
  }
}

export function truncate(text: string | null | undefined, length = 40): string {
  if (!text) return "";
  if (text.length <= length) return text;
  return text.slice(0, length).trimEnd() + "…";
}
