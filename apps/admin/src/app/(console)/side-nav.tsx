"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

interface NavItem {
  href: string;
  label: string;
  /** Module prévu par le cahier des charges mais pas encore livré (lot P1/P2). */
  planned?: string;
}

const SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: "Pilotage",
    items: [{ href: "/dashboard", label: "Tableau de bord" }],
  },
  {
    title: "Catalogue",
    items: [
      { href: "/catalog/offers", label: "Offres" },
      { href: "/catalog/reviews", label: "Revue des preuves" },
      { href: "/catalog/providers", label: "Fournisseurs", planned: "P1" },
    ],
  },
  {
    title: "Opérations",
    items: [
      { href: "/operations/catalog-sync", label: "Sources & batch", planned: "P1" },
      { href: "/operations/audit", label: "Audit", planned: "P1" },
    ],
  },
  {
    title: "Activité",
    items: [{ href: "/users", label: "Utilisateurs", planned: "P2" }],
  },
];

export function SideNav() {
  return <NavList pathname={usePathname()} />;
}

/** Rendu sans hook : sert aussi de repli statique pendant le streaming (pathname null). */
export function NavList({ pathname }: { pathname: string | null }) {
  return (
    <nav aria-label="Navigation principale" className="px-2 pb-4">
      {SECTIONS.map((section) => (
        <div key={section.title} className="mt-3 first:mt-0">
          <p className="px-2 pb-1 text-xs font-medium uppercase tracking-wide text-muted">{section.title}</p>
          <ul className="flex flex-wrap gap-1 md:block md:space-y-0.5">
            {section.items.map((item) => {
              if (item.planned) {
                return (
                  <li key={item.href}>
                    <span
                      className="flex items-center justify-between gap-2 rounded-md px-2 py-1.5 text-muted/70"
                      aria-disabled="true"
                      title="Module prévu, pas encore disponible"
                    >
                      {item.label}
                      <span className="rounded bg-background px-1.5 text-[10px] font-semibold">{item.planned}</span>
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
                    className={`block rounded-md px-2 py-1.5 font-medium ${
                      active ? "bg-lavender-soft text-lavender-strong" : "text-foreground hover:bg-background"
                    }`}
                  >
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
