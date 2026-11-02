import { createHmac, timingSafeEqual } from "crypto";

const CSRF_MAX_AGE_SECONDS = 60 * 15; // 15 minutes, aligned with JWT TTL
const MIN_SECRET_BYTES = 32;

/**
 * Secret CSRF côté serveur.
 * En production, doit être défini via la variable d'environnement CSRF_SECRET
 * (valeur aléatoire générée, absente du dépôt Git).
 */
function getCsrfSecret(): string {
  const secret = process.env.CSRF_SECRET;
  if (!secret || Buffer.byteLength(secret, "utf8") < MIN_SECRET_BYTES) {
    throw new Error("CSRF_SECRET must be configured server-side with at least 32 bytes");
  }
  return secret;
}

/**
 * Génère un jeton CSRF signé côté serveur.
 * Format : timestamp:nonce:hex_signature
 * Le payload contient uniquement : timestamp:nonce
 * La signature est liée au cookie de session opaque, jamais au JWT public.
 * Le jeton public ne contient que des éléments non sensibles.
 */
export function generateCsrfToken(sessionBinding: string): string {
  if (!sessionBinding) throw new Error("A session binding is required to issue a CSRF token");

  const timestamp = Date.now();
  const nonceBytes = new Uint8Array(8);
  crypto.getRandomValues(nonceBytes);
  const nonce = Array.from(nonceBytes, (b) => b.toString(16).padStart(2, "0")).join("");
  const payload = `${timestamp}:${nonce}`;
  const signature = createHmac("sha256", getCsrfSecret())
    .update(`${payload}:${sessionBinding}`)
    .digest("hex");
  return `${payload}:${signature}`;
}

/**
 * Vérifie un jeton CSRF signé pour la session actuellement authentifiée.
 * Retourne true si la signature est valide, le jeton non expiré.
 */
export function verifyCsrfToken(token: string | null, sessionBinding: string | null): boolean {
  if (!token || !sessionBinding) return false;

  // Format : timestamp:nonce:hex_signature
  const lastColon = token.lastIndexOf(":");
  if (lastColon === -1) return false;

  const payload = token.slice(0, lastColon);
  const signatureHex = token.slice(lastColon + 1);

  if (!payload || !signatureHex) return false;

  const expectedSignature = createHmac("sha256", getCsrfSecret())
    .update(`${payload}:${sessionBinding}`)
    .digest("hex");

  // Les signatures doivent avoir la même longueur pour la comparaison à temps constant
  if (signatureHex.length !== expectedSignature.length) return false;

  // Comparaison à temps constant pour éviter les attaques par timing
  if (!timingSafeEqual(Buffer.from(signatureHex), Buffer.from(expectedSignature))) {
    return false;
  }

  // Décoder le payload pour vérifier l'expiration
  // Format : timestamp:nonce — le timestamp est avant le premier deux-points
  const firstColon = payload.indexOf(":");
  if (firstColon === -1) return false;

  const tokenTimestamp = Number(payload.slice(0, firstColon));
  if (!Number.isSafeInteger(tokenTimestamp) || tokenTimestamp > Date.now()) return false;

  // Le jeton ne doit pas être expiré
  const now = Date.now();
  if (now - tokenTimestamp > CSRF_MAX_AGE_SECONDS * 1000) return false;

  return true;
}

export { CSRF_MAX_AGE_SECONDS };
