import "server-only";

/** URL de l'API Spring Boot, lue côté serveur uniquement (jamais exposée au navigateur). */
export function apiBaseUrl(): string {
  return (process.env.EPARLY_API_BASE_URL ?? "http://127.0.0.1:8080").replace(/\/+$/, "");
}

/**
 * Garde-fou : la publication est refusée par le BFF tant qu'elle n'est pas explicitement
 * autorisée pour l'environnement (cahier des charges §5.7 : aucune publication en développement).
 */
export function publicationEnabled(): boolean {
  return process.env.EPARLY_ADMIN_ALLOW_PUBLISH === "true";
}

export const isProduction = process.env.NODE_ENV === "production";
