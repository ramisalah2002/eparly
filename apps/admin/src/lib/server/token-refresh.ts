import type { TokenResponse } from "@/lib/types";

// Rotation des refresh tokens. Spring Boot révoque toute la famille si un refresh token
// consommé est rejoué : deux rafraîchissements concurrents avec le même jeton
// déconnecteraient l'administrateur. On mutualise donc la requête par jeton et on garde
// brièvement son résultat pour les requêtes parallèles du même navigateur.
//
// Limite : ce cache est local au processus Node. Avec plusieurs instances du back-office,
// il faudrait un verrou partagé (à prévoir avant un déploiement multi-instance).

const RESULT_TTL_MS = 15_000;

type RefreshResult = TokenResponse | null;

const inFlight = new Map<string, Promise<RefreshResult>>();
const recent = new Map<string, { result: RefreshResult; at: number }>();

export async function refreshTokens(baseUrl: string, refreshToken: string): Promise<RefreshResult> {
  const now = Date.now();
  for (const [key, entry] of recent) {
    if (now - entry.at > RESULT_TTL_MS) recent.delete(key);
  }
  const cached = recent.get(refreshToken);
  if (cached) return cached.result;

  const pending = inFlight.get(refreshToken);
  if (pending) return pending;

  const promise = (async (): Promise<RefreshResult> => {
    try {
      const response = await fetch(`${baseUrl}/v1/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ refreshToken }),
        cache: "no-store",
      });
      if (!response.ok) return null;
      return (await response.json()) as TokenResponse;
    } catch {
      // Réseau indisponible : on ne rejoue pas le jeton (il a pu être consommé).
      return null;
    }
  })();

  inFlight.set(refreshToken, promise);
  try {
    const result = await promise;
    recent.set(refreshToken, { result, at: Date.now() });
    return result;
  } finally {
    inFlight.delete(refreshToken);
  }
}
