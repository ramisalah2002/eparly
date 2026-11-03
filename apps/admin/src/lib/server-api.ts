import { cookies } from "next/headers";
import { filterForwardableHeaders, SESSION_COOKIE, REFRESH_COOKIE } from "@/lib/api";

/**
 * URL du backend Spring Boot (cÃīté serveur uniquement).
 * Jamais exposée au navigateur.
 */
const SPRING_BOOT_BASE =
  process.env.API_BASE ?? "http://localhost:8080";

/**
 * Verrou logique d'Éviction pour le renouvellement de session.
 * Comme les refresh tokens sont rotatifs (Spring Boot consomme l'ancien et
 * en Émet un nouveau), deux requâses simultanÉes ne doivent pas tenter
 * de rafraÎchir avec le mÃame token. Un verrou mÃemoire local ne protâge
 * pas plusieurs instances de dÉploiement, mais il Évite les collisions
 * au sein d'une mÃame instance. Entre instances, Spring Boot gâre dÉjĂ
 * la revocation de famille : si deux requâses utilisent le mÃame refresh
 * token, l'une rÉussit et l'autre reçoit 401, ce qui est le comportement
 * attendu (le client doit se reconnecter).
 */
const refreshLocks = new Map<string, Promise<RefreshResult | null>>();

/**
 * Client HTTP cÃīté serveur (BFF).
 * Transmet le jeton d'accâs depuis le cookie HttpOnly vers Spring Boot
 * en tant qu'en-tâe Authorization: Bearer.
 * Jamais exposé au navigateur : le JWT ne sort pas du cÃīté serveur.
 */
export async function serverApiRequest(
  input: string,
  init: RequestInit = {},
): Promise<Response> {
  const cookieStore = await cookies();
  const sessionToken = cookieStore.get(SESSION_COOKIE)?.value;

  const headers: Record<string, string> = filterForwardableHeaders(
    (init.headers as Headers | undefined) ?? new Headers(),
  );

  if (sessionToken) {
    headers["Authorization"] = `Bearer ${sessionToken}`;
  }

  return fetch(`${SPRING_BOOT_BASE}${input}`, {
    ...init,
    headers,
  });
}

/**
 * RÉcupâre le jeton de rafraÎchissement depuis le cookie HttpOnly
 * (cÃīté serveur uniquement).
 */
export async function getRefreshTokenFromCookie(): Promise<string | null> {
  const cookieStore = await cookies();
  return cookieStore.get(REFRESH_COOKIE)?.value ?? null;
}

/**
 * RÉcupâre le jeton de session depuis le cookie HttpOnly
 * (cÃīté serveur uniquement).
 */
export async function getSessionTokenFromCookie(): Promise<string | null> {
  const cookieStore = await cookies();
  return cookieStore.get(SESSION_COOKIE)?.value ?? null;
}

/**
 * RÉsultat du renouvellement de session.
 */
export interface RefreshResult {
  accessToken: string;
  expiresInSeconds: number;
  newRefreshToken?: string;
  refreshExpiresInSeconds?: number;
}

/**
 * Tente de rafraÎchir la session via Spring Boot (/v1/auth/refresh).
 * Utilise un verrou par instance pour Éviter que deux requâses
 * simultanÉes consomment le mÃame refresh token rotatif.
 * Retourne le nouveau jeton d'accâs et sa durÉe de vie, ou null en cas d'âchec.
 */
export async function refreshSession(): Promise<RefreshResult | null> {
  const refreshToken = await getRefreshTokenFromCookie();
  if (!refreshToken) return null;

  // Verrou par token de refresh : si une requâse est dÉjÃ en cours
  // de rafraÎchissement avec ce mÃame token, attendre son rÉsultat.
  const existing = refreshLocks.get(refreshToken);
  if (existing) {
    return existing;
  }

  const refreshPromise = (async () => {
    try {
      const response = await serverApiRequest("/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) return null;

      const data = await response.json();
      const accessToken = data.accessToken as string | undefined;
      const expiresIn = data.expiresIn as number | undefined;
      const newRefreshToken = data.refreshToken as string | undefined;
      const refreshExpiresIn = data.refreshExpiresIn as number | undefined;

      if (!accessToken || expiresIn == null) return null;

      return {
        accessToken,
        expiresInSeconds: expiresIn,
        ...(newRefreshToken ? { newRefreshToken } : {}),
        ...(refreshExpiresIn != null ? { refreshExpiresInSeconds: refreshExpiresIn } : {}),
      };
    } catch {
      return null;
    } finally {
      refreshLocks.delete(refreshToken);
    }
  })();

  refreshLocks.set(refreshToken, refreshPromise);
  return refreshPromise;
}

/**
 * Tente de renouveler la session lorsque le cookie d'accâs est absent
 * ou expiré mais que le cookie de refresh est encore valide.
 * Appelé par les routes proxy avant de rejeter avec 401.
 */
export async function ensureSessionOrRefresh(): Promise<RefreshResult | null> {
  const sessionToken = await getSessionTokenFromCookie();
  const refreshToken = await getRefreshTokenFromCookie();

  // Si nous avons dÉjÃ un token de session, rien à renouveler
  if (sessionToken) return null;

  // Pas de session mais un refresh token existe : tenter le renouvellement
  if (refreshToken) {
    return refreshSession();
  }

  return null;
}

/**
 * Options de cookie communes pour la session.
 * @param maxAgeSeconds DurÉe de vie du cookie en secondes (alignÉe sur le TTL du JWT).
 */
export function sessionCookieOptions(maxAgeSeconds?: number): Record<string, unknown> {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    ...(maxAgeSeconds != null ? { maxAge: maxAgeSeconds } : {}),
  };
}
