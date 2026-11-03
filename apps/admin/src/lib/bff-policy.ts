// Règles du BFF /api/admin/*, isolées ici pour être testées sans Next.js.

export type MutationMethod = "POST" | "PUT" | "PATCH";

interface AllowedRoute {
  method: MutationMethod;
  pattern: RegExp;
  /** Publication : soumise au garde-fou d'environnement. */
  publication?: boolean;
}

const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

/**
 * Seules les mutations utilisées par les écrans livrés transitent par le BFF.
 * Spring Boot reste l'autorité : cette liste réduit la surface exposée, elle n'autorise rien.
 */
const ALLOWED: AllowedRoute[] = [
  { method: "PATCH", pattern: new RegExp(`^catalog/offers/${UUID}/evidences/${UUID}/verification$`) },
  { method: "POST", pattern: new RegExp(`^catalog/offers/${UUID}/publish$`), publication: true },
  { method: "POST", pattern: new RegExp(`^catalog/offers/${UUID}/archive$`) },
];

export type RouteDecision =
  | { allowed: true; backendPath: string; publication: boolean }
  | { allowed: false; status: 404 | 405 };

export function resolveAdminRoute(method: string, segments: string[]): RouteDecision {
  const relative = segments.join("/");
  if (segments.some((s) => s === "" || s === "." || s === "..")) return { allowed: false, status: 404 };
  const matches = ALLOWED.filter((route) => route.pattern.test(relative));
  if (matches.length === 0) return { allowed: false, status: 404 };
  const route = matches.find((r) => r.method === method);
  if (!route) return { allowed: false, status: 405 };
  return { allowed: true, backendPath: `/v1/admin/${relative}`, publication: route.publication === true };
}

/** Double-submit : le jeton du header doit égaler celui du cookie, en temps constant. */
export function csrfTokensMatch(cookieToken: string | undefined, headerToken: string | null): boolean {
  if (!cookieToken || !headerToken || cookieToken.length < 32) return false;
  if (cookieToken.length !== headerToken.length) return false;
  let diff = 0;
  for (let i = 0; i < cookieToken.length; i += 1) {
    diff |= cookieToken.charCodeAt(i) ^ headerToken.charCodeAt(i);
  }
  return diff === 0;
}

/** Refuse les requêtes dont l'origine déclarée n'est pas le back-office lui-même. */
export function sameOrigin(originHeader: string | null, requestUrl: string): boolean {
  if (!originHeader) return false;
  try {
    return new URL(originHeader).origin === new URL(requestUrl).origin;
  } catch {
    return false;
  }
}
