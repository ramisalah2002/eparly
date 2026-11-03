import { Smartphone, Tv, Wifi, type LucideIcon } from "lucide-react";
import type { Sector } from "@/lib/types";

// Pastels repris de l'app mobile (category-theme.ts) pour rester homogène.
const SECTOR_THEMES: Record<Sector, { icon: LucideIcon; className: string }> = {
  STREAMING: { icon: Tv, className: "bg-[#F8BBD0]/50 text-[#9C1450]" },
  MOBILE_PLAN: { icon: Smartphone, className: "bg-[#BBDEFB]/60 text-[#0F4F94]" },
  INTERNET_BOX: { icon: Wifi, className: "bg-[#B2DFDB]/60 text-[#00594F]" },
};

export function SectorIcon({ sector }: { sector: Sector }) {
  const { icon: Icon, className } = SECTOR_THEMES[sector];
  return (
    <span aria-hidden className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${className}`}>
      <Icon className="size-4" strokeWidth={2.2} />
    </span>
  );
}
