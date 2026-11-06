import { redirect } from "next/navigation";
import { getCurrentAdmin } from "@/lib/server/identity";
import { LogoutButton } from "./logout-button";

function initials(name: string): string {
  const parts = name.split(/[\s@.]+/).filter(Boolean);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase() || "?";
}

export async function AdminBadge() {
  const identity = await getCurrentAdmin();
  if (!identity.ok) {
    if (identity.status === 401) redirect("/login");
    return <span className="text-muted">Identité non disponible</span>;
  }
  const { fullName, email } = identity.data;
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border bg-surface py-1.5 pl-1.5 pr-2 shadow-card">
      <span aria-hidden className="flex size-8 items-center justify-center rounded-full bg-beige-soft text-xs font-semibold text-[#6b5435]">
        {initials(fullName || email)}
      </span>
      <span className="leading-tight">
        <span className="block font-medium">{fullName || email}</span>
        {fullName && <span className="block text-xs text-muted">{email}</span>}
      </span>
      <LogoutButton />
    </div>
  );
}
