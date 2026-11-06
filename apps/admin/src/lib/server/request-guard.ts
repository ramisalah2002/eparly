import "server-only";
import { NextResponse, type NextRequest } from "next/server";
import { csrfTokensMatch, sameOrigin } from "@/lib/bff-policy";
import { CSRF_COOKIE, CSRF_HEADER } from "@/lib/session-cookies";

/** Erreur rédigée par le BFF lui-même : le client l'affiche telle quelle (`localized`). */
export function bffError(status: number, message: string): NextResponse {
  return NextResponse.json({ status, message, localized: true }, { status });
}

/** Contrôle commun à toute mutation du BFF : même origine + jeton CSRF double-submit. */
export function rejectUnsafeMutation(request: NextRequest): NextResponse | null {
  if (!sameOrigin(request.headers.get("origin"), request.nextUrl.href)) {
    return bffError(403, "Origine de la requête refusée.");
  }
  if (!csrfTokensMatch(request.cookies.get(CSRF_COOKIE)?.value, request.headers.get(CSRF_HEADER))) {
    return bffError(403, "Jeton CSRF invalide. Rechargez la page.");
  }
  return null;
}

export async function readJsonBody(request: NextRequest, maxBytes = 16_384): Promise<unknown | undefined> {
  const text = await request.text();
  if (text.length > maxBytes) return undefined;
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
