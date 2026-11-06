import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { describeApiError } from "@/lib/format";
import { callBackend } from "@/lib/server/backend";
import { hasCatalogAdminPermission } from "@/lib/server/catalog";
import { isProduction } from "@/lib/server/config";
import { bffError as error, readJsonBody, rejectUnsafeMutation } from "@/lib/server/request-guard";
import { ACCESS_COOKIE, REFRESH_COOKIE, sessionCookieOptions } from "@/lib/session-cookies";
import type { TokenResponse } from "@/lib/types";

export async function POST(request: NextRequest) {
  const rejected = rejectUnsafeMutation(request);
  if (rejected) return rejected;

  const body = await readJsonBody(request, 2_048);
  const email = typeof (body as { email?: unknown })?.email === "string" ? (body as { email: string }).email.trim() : "";
  const password = typeof (body as { password?: unknown })?.password === "string" ? (body as { password: string }).password : "";
  if (!email || !password || email.length > 320 || password.length > 128) {
    return error(400, "Email et mot de passe requis.");
  }

  const login = await callBackend<TokenResponse>("/v1/auth/login", {
    method: "POST",
    body: { email, password },
    accessToken: "",
  });
  if (!login.ok) {
    if (login.status === 401) return error(401, "Identifiants invalides ou compte non vérifié.");
    return error(login.status || 502, describeApiError(login.status, login.error?.message));
  }

  const tokens = login.data;
  const permission = await hasCatalogAdminPermission(tokens.accessToken);
  if (!permission.ok) {
    // Compte valide mais non administrateur : on révoque aussitôt la session ouverte.
    await callBackend("/v1/auth/logout", { method: "POST", body: { refreshToken: tokens.refreshToken }, accessToken: "" });
    if (permission.status === 403) {
      return error(403, "Ce compte ne dispose pas de la permission CATALOG_ADMIN.");
    }
    return error(permission.status || 502, describeApiError(permission.status, permission.error?.message));
  }

  const store = await cookies();
  store.set(ACCESS_COOKIE, tokens.accessToken, sessionCookieOptions(isProduction, tokens.expiresIn));
  store.set(REFRESH_COOKIE, tokens.refreshToken, sessionCookieOptions(isProduction, tokens.refreshExpiresIn));
  return NextResponse.json({ ok: true });
}
