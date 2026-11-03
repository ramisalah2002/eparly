import "server-only";
import { cache } from "react";
import type { AdminIdentity } from "@/lib/types";
import type { BackendResult } from "./backend";
import { getProfile } from "./catalog";

/** Identité réduite de l'administrateur connecté, mutualisée pour un même rendu. */
export const getCurrentAdmin = cache(async (): Promise<BackendResult<AdminIdentity>> => {
  const profile = await getProfile();
  if (!profile.ok) return profile;
  const { id, email, fullName } = profile.data;
  return { ok: true, status: 200, data: { id, email, fullName } };
});
