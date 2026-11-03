"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { buttonClass } from "@/components/button-class";
import { bffMutation } from "@/lib/client/bff";

export function LogoutButton() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function logout() {
    setBusy(true);
    await bffMutation("/api/auth/logout", "POST");
    router.replace("/login");
    router.refresh();
  }

  return (
    <button type="button" className={buttonClass("secondary")} onClick={logout} disabled={busy}>
      {busy ? "Déconnexion…" : "Se déconnecter"}
    </button>
  );
}
