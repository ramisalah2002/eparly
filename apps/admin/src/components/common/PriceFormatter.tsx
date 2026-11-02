"use client";

interface PriceFormatterProps {
  amountMinor: number | null | undefined;
  currency?: string | null | undefined;
  billingPeriod?: string | null | undefined;
}

export function PriceFormatter({
  amountMinor,
  currency = "EUR",
  billingPeriod,
}: PriceFormatterProps) {
  if (amountMinor === null || amountMinor === undefined) {
    return <span className="text-zinc-400">Non renseigné</span>;
  }

  const amount = amountMinor / 100;
  const formatted = new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: currency ?? "EUR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);

  const periodLabel = billingPeriod
    ? ` / ${billingPeriod.charAt(0).toUpperCase() + billingPeriod.slice(1).toLowerCase()}`
    : "";

  return <span className="font-medium">{formatted}{periodLabel}</span>;
}
