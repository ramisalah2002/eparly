import { Suspense, type ReactNode } from "react";
import { AdminBadge } from "./admin-badge";
import { NavList, SideNav } from "./side-nav";

export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="border-b border-border bg-surface md:sticky md:top-0 md:h-screen md:w-60 md:shrink-0 md:border-b-0 md:border-r">
        <div className="flex items-center justify-between px-4 py-4 md:block">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-lavender-strong">Eparly</p>
            <p className="font-semibold">Admin</p>
          </div>
        </div>
        <Suspense fallback={<NavList pathname={null} />}>
          <SideNav />
        </Suspense>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-end gap-3 border-b border-border bg-surface px-4 py-2 md:px-8">
          <Suspense fallback={<span className="h-5 w-40 animate-pulse rounded bg-border/60" />}>
            <AdminBadge />
          </Suspense>
        </header>
        <main className="flex-1 px-4 py-6 md:px-8">{children}</main>
      </div>
    </div>
  );
}
