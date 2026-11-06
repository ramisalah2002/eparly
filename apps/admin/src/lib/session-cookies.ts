// Noms et options des cookies de session admin. Module sans dépendance serveur :
// il est partagé par le proxy, les route handlers et le client (nom du cookie CSRF).

export const ACCESS_COOKIE = "eparly_admin_at";
export const REFRESH_COOKIE = "eparly_admin_rt";
export const CSRF_COOKIE = "eparly_admin_csrf";
export const CSRF_HEADER = "x-csrf-token";

/** Marge avant expiration à partir de laquelle le proxy fait tourner le jeton d'accès. */
export const ACCESS_REFRESH_SKEW_SECONDS = 30;

export interface CookieOptions {
  httpOnly: boolean;
  secure: boolean;
  sameSite: "strict" | "lax";
  path: string;
  maxAge?: number;
}

export function sessionCookieOptions(secure: boolean, maxAge?: number): CookieOptions {
  return { httpOnly: true, secure, sameSite: "strict", path: "/", maxAge };
}

/** Le cookie CSRF doit être lisible par le JavaScript client (double-submit). */
export function csrfCookieOptions(secure: boolean): CookieOptions {
  return { httpOnly: false, secure, sameSite: "strict", path: "/" };
}

/**
 * Lit l'expiration d'un JWT sans le valider. Sert uniquement à décider d'un refresh :
 * l'authenticité du jeton reste vérifiée par Spring Boot à chaque appel.
 */
export function jwtExpiresAt(token: string): number | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const json = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof json.exp === "number" ? json.exp : null;
  } catch {
    return null;
  }
}

export function needsRefresh(token: string | undefined, nowSeconds: number): boolean {
  if (!token) return true;
  const exp = jwtExpiresAt(token);
  return exp === null || exp - ACCESS_REFRESH_SKEW_SECONDS <= nowSeconds;
}
