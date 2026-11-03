"use client";

import {
  Activity,
  Building2,
  ClipboardCheck,
  LayoutDashboard,
  ScrollText,
  Tags,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Module prévu par le cahier des charges mais pas encore livré (lot P1/P2). */
  planned?: string;
}

const SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: "Pilotage",
    items: [{ href: "/dashboard", label: "Tableau de bord", icon: LayoutDashboard }],
  },
  {
    title: "Catalogue",
    items: [
      { href: "/catalog/offers", label: "Offres", icon: Tags },
      { href: "/catalog/reviews", label: "Revue des preuves", icon: ClipboardCheck },
      { href: "/catalog/providers", label: "Fournisseurs", icon: Building2, planned: "P1" },
    ],
  },
  {
    title: "Opérations",
    items: [
      { href: "/operations/catalog-sync", label: "Sources & batch", icon: Activity, planned: "P1" },
      { href: "/operations/audit", label: "Audit", icon: ScrollText, planned: "P1" },
    ],
  },
  {
    title: "Activité",
    items: [{ href: "/users", label: "Utilisateurs", icon: Users, planned: "P2" }],
  },
];

export function SideNav() {
  return <NavList pathname={usePathname()} />;
}

/** Rendu sans hook : sert aussi de repli statique pendant le streaming (pathname null). */
export function NavList({ pathname }: { pathname: string | null }) {
  return (
    <nav aria-label="Navigation principale" className="px-3 pb-4">
      {SECTIONS.map((section) => (
        <div key={section.title} className="mt-4 first:mt-0">
          <p className="px-3 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted">{section.title}</p>
          <ul className="flex flex-wrap gap-1 md:block md:space-y-1">
            {section.items.map((item) => {
              const Icon = item.icon;
              if (item.planned) {
                return (
                  <li key={item.href}>
                    <span
                      className="flex items-center gap-2.5 rounded-2xl px-3 py-2 text-muted/70"
                      aria-disabled="true"
                      title="Module prévu, pas encore disponible"
                    >
                      <Icon aria-hidden className="size-4" strokeWidth={2} />
                      <span className="flex-1">{item.label}</span>
                      <span className="rounded-full bg-background px-1.5 text-[10px] font-semibold">{item.planned}</span>
                    </span>
                  </li>
                );
              }
              const active = pathname !== null && (pathname === item.href || pathname.startsWith(`${item.href}/`));
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={`flex items-center gap-2.5 rounded-2xl px-3 py-2 font-medium transition-colors ${
                      active ? "bg-lavender-soft text-lavender-strong" : "text-foreground hover:bg-background"
                    }`}
                  >
                    <Icon aria-hidden className="size-4" strokeWidth={active ? 2.4 : 2} />
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );
}
