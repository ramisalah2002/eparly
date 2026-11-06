import { Suspense, type ReactNode } from "react";
import { EparlyLogo } from "@/components/eparly-logo";
import { AdminBadge } from "./admin-badge";
import { NavList, SideNav } from "./side-nav";

export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="border-b border-border bg-surface md:sticky md:top-0 md:h-screen md:w-64 md:shrink-0 md:border-b-0 md:border-r">
        <div className="flex items-center gap-2 px-6 py-5">
          <EparlyLogo width={92} />
          <span className="rounded-full bg-lavender-soft px-2 py-0.5 text-[11px] font-semibold text-lavender-strong">Admin</span>
        </div>
        <Suspense fallback={<NavList pathname={null} />}>
          <SideNav />
        </Suspense>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-end gap-3 px-4 py-3 md:px-8">
          <Suspense fallback={<span className="h-9 w-48 animate-pulse rounded-2xl bg-border" />}>
            <AdminBadge />
          </Suspense>
        </header>
        <main className="flex-1 px-4 pb-10 md:px-8">{children}</main>
      </div>
    </div>
  );
}
