import { redirect } from "next/navigation";
import { getCurrentAdmin } from "@/lib/server/identity";
import { LogoutButton } from "./logout-button";

export async function AdminBadge() {
  const identity = await getCurrentAdmin();
  if (!identity.ok) {
    if (identity.status === 401) redirect("/login");
    return <span className="text-muted">Identité non disponible</span>;
  }
  const { fullName, email } = identity.data;
  return (
    <div className="flex items-center gap-3">
      <span className="text-right leading-tight">
        <span className="block font-medium">{fullName || email}</span>
        {fullName && <span className="block text-xs text-muted">{email}</span>}
      </span>
      <LogoutButton />
    </div>
  );
}
