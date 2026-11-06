"use client";

import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { apiRequest } from "@/lib/api";
import {
  Building2,
  LayoutDashboard,
  ListChecks,
  ShieldCheck,
  LogOut,
  ChevronRight,
} from "lucide-react";

const navItems = [
  { href: "/dashboard", label: "Tableau de bord", icon: LayoutDashboard },
  { href: "/catalog/offers", label: "Offres", icon: ListChecks },
  { href: "/catalog/providers", label: "Fournisseurs", icon: Building2 },
  { href: "/catalog/reviews", label: "Centre de validation", icon: ShieldCheck },
];

export default function CatalogLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();

  const handleLogout = async () => {
    try {
      await apiRequest("/api/auth/logout", {
        method: "POST",
      });
    } catch {
      // Ignorer les erreurs de déconnexion cÃīté serveur
    }
    router.replace("/login");
  };

  return (
    <div className="flex h-screen flex-col lg:flex-row overflow-hidden bg-zinc-50">
      <aside
        className="hidden lg:flex lg:w-64 flex-col border-r border-zinc-200 bg-white z-20"
      >
        <div className="flex h-16 items-center justify-between border-b border-zinc-200 bg-zinc-900 px-6">
          <span className="font-bold text-white tracking-tight">Eparly Admin</span>
        </div>
        <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(`${item.href}/`));
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`
                  block flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors
                  ${active
                    ? "bg-blue-600 text-white"
                    : "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900"
                }`}
              >
                <Icon size={18} />
                {item.label}
              </Link>
            );
          })}
          <div className="mt-8 pt-4 border-t border-zinc-200">
            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 transition-colors"
            >
              <LogOut size={18} /> Déconnexion
            </button>
          </div>
        </nav>
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden relative">
        <header className="flex h-16 items-center justify-between border-b border-zinc-200 bg-white px-6 lg:px-8 shrink-0">
          <div className="flex items-center gap-3">
            <ChevronRight size={16} className="text-zinc-400" />
            <h1 className="text-sm font-semibold text-zinc-900">
              {pathname.startsWith("/catalog/providers")
                ? "Gestion des fournisseurs"
                : pathname.startsWith("/catalog/offers")
                  ? "Gestion des offres"
                  : "Centre de validation"}
            </h1>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-xs text-zinc-500">Eparly Admin</span>
          </div>
        </header>
        <nav aria-label="Navigation principale" className="flex gap-2 overflow-x-auto border-b border-zinc-200 bg-white px-4 py-2 lg:hidden">
          {navItems.filter((item) => item.href !== "/dashboard").map((item) => {
            const Icon = item.icon;
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`inline-flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium ${
                  active ? "bg-blue-600 text-white" : "text-zinc-600 hover:bg-zinc-100"
                }`}
              >
                <Icon size={15} />{item.label}
              </Link>
            );
          })}
        </nav>
        <main className="flex-1 overflow-y-auto p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}
