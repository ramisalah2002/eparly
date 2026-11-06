import {
  AppWindow,
  Cloud,
  Dumbbell,
  Flame,
  Gamepad2,
  Landmark,
  Music,
  Newspaper,
  Repeat,
  Smartphone,
  TrainFront,
  Tv,
  Umbrella,
  Wifi,
  Zap,
  type LucideIcon,
} from "lucide-react";
import type { Sector } from "@/lib/types";

// Pastels repris de l'app mobile (category-theme.ts) pour les secteurs historiques ;
// les autres secteurs partagent le lavande de la marque.
const NEUTRAL = "bg-lavender-soft text-lavender-strong";
const SECTOR_THEMES: Record<Sector, { icon: LucideIcon; className: string }> = {
  STREAMING: { icon: Tv, className: "bg-[#F8BBD0]/50 text-[#9C1450]" },
  MOBILE_PLAN: { icon: Smartphone, className: "bg-[#BBDEFB]/60 text-[#0F4F94]" },
  INTERNET_BOX: { icon: Wifi, className: "bg-[#B2DFDB]/60 text-[#00594F]" },
  MUSIC_STREAMING: { icon: Music, className: NEUTRAL },
  CLOUD_STORAGE: { icon: Cloud, className: NEUTRAL },
  SOFTWARE_SAAS: { icon: AppWindow, className: NEUTRAL },
  GAMING: { icon: Gamepad2, className: NEUTRAL },
  ELECTRICITY: { icon: Zap, className: NEUTRAL },
  GAS: { icon: Flame, className: NEUTRAL },
  INSURANCE: { icon: Umbrella, className: NEUTRAL },
  BANKING: { icon: Landmark, className: NEUTRAL },
  SPORT_FITNESS: { icon: Dumbbell, className: NEUTRAL },
  PRESS: { icon: Newspaper, className: NEUTRAL },
  TRANSPORT: { icon: TrainFront, className: NEUTRAL },
  OTHER_RECURRING: { icon: Repeat, className: NEUTRAL },
};

export function SectorIcon({ sector }: { sector: Sector }) {
  // Repli si l'API ajoute un secteur avant que le back-office ne le connaisse.
  const { icon: Icon, className } = SECTOR_THEMES[sector] ?? { icon: Repeat, className: NEUTRAL };
  return (
    <span aria-hidden className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${className}`}>
      <Icon className="size-4" strokeWidth={2.2} />
    </span>
  );
}
