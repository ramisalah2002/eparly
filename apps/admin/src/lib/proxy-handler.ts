import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import {
  ensureSessionOrRefresh,
  serverApiRequest,
  sessionCookieOptions,
} from "@/lib/server-api";
import { SESSION_BINDING_COOKIE, SESSION_COOKIE } from "@/lib/api";
import { generateCsrfToken, verifyCsrfToken } from "@/lib/csrf";
import { randomBytes } from "crypto";

/**
 * Mâthodes HTTP autorisÉes par groupe de routes.
 * Seules les mâthodes rÉellement exposÉes par Spring Boot sont autorisÉes.
 */
export const ALLOWED_METHODS: Record<string, string[]> = {
  "/api/auth/login": ["POST"],
  "/api/auth/logout": ["POST"],
  "/api/auth/me": ["GET"],
  // Catalogue admin : lecture, modification, prix, preuves, publication, archivage
  "/api/admin/catalog": ["GET", "POST", "PATCH", "PUT", "DELETE"],
  // Catalogue public : lecture seule
  "/api/offers": ["GET"],
};

/**
 * DÉtermine si une mâthode est autorisÉe pour une donnÉe route.
 */
export function isMethodAllowed(pathname: string, method: string): boolean {
  const upperMethod = method.toUpperCase();
  // VÉrification exacte d'abord
  if (ALLOWED_METHODS[pathname]) {
    return ALLOWED_METHODS[pathname].includes(upperMethod);
  }
  // VÉrification par prÉfixe pour les routes dynamiques
  for (const [prefix, methods] of Object.entries(ALLOWED_METHODS)) {
    if (pathname.startsWith(prefix) && methods.includes(upperMethod)) {
      return true;
    }
  }
  return false;
}

/**
 * VÉrifie le jeton CSRF signé pour la session courante, ainsi que l'origine.
 * Le jeton CSRF ne contient aucun identifiant de session ni JWT.
 * Retourne true si la requâse est autorisÉe.
 */
export function verifyRequestCsrf(request: Request): boolean {
  // Le login n'exige pas de session prÉexistante : pas de vÉrification CSRF.
  const url = new URL(request.url);
  if (url.pathname === "/api/auth/login") {
    return true;
  }

  if (!["POST", "PATCH", "PUT", "DELETE"].includes(request.method)) {
    return true;
  }

  const csrfToken = request.headers.get("X-CSRF-TOKEN");
  if (!csrfToken) return false;

  const sessionBinding = request.headers
    .get("Cookie")
    ?.split(";")
    .map((cookie) => cookie.trim())
    .find((cookie) => cookie.startsWith(`${SESSION_BINDING_COOKIE}=`))
    ?.slice(SESSION_BINDING_COOKIE.length + 1);
  if (!verifyCsrfToken(csrfToken, sessionBinding ?? null)) return false;

  // Validation de l'origine pour les requâses mutantes
  const origin = request.headers.get("Origin");
  const referer = request.headers.get("Referer");
  const host = request.headers.get("Host")?.toLowerCase();
  const matchesHost = (value: string): boolean => {
    try {
      const parsed = new URL(value);
      return (parsed.protocol === "http:" || parsed.protocol === "https:") &&
        Boolean(host) && parsed.host.toLowerCase() === host;
    } catch {
      return false;
    }
  };

  if (origin && !matchesHost(origin)) return false;
  if (referer && !matchesHost(referer)) return false;

  return true;
}

/**
 * Transmet une rÉponse Spring Boot au client, en filtrant les headers sensibles.
 */
export function forwardResponse(springResponse: Response): NextResponse {
  const headers: Record<string, string> = {};
  springResponse.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (
      lower !== "content-length" &&
      lower !== "transfer-encoding" &&
      lower !== "connection"
    ) {
      headers[key] = value;
    }
  });

  return new NextResponse(springResponse.body, {
    status: springResponse.status,
    headers,
  });
}

/**
 * Exâcute une requâse proxÉe vers Spring Boot avec gestion du renouvellement
 * de session et retour des nouveaux cookies aprâs rotation.
 */
export async function proxiedRequest(
  request: Request,
  path: string,
  allowedMethods: string[],
): Promise<NextResponse> {
  const method = request.method.toUpperCase();

  if (!allowedMethods.includes(method)) {
    return NextResponse.json(
      { error: "Mâthode non autorisÉe pour cette route" },
      { status: 405 },
    );
  }

  const cookieStore = await cookies();

  // VÉrification CSRF (le jeton CSRF ne contient aucun identifiant de session ni JWT)
  if (!verifyRequestCsrf(request)) {
    return NextResponse.json(
      { error: "Jeton CSRF invalide ou manquant" },
      { status: 403 },
    );
  }

  // Premiâre tentative
  let springResponse = await serverApiRequest(path, {
    method: request.method,
    headers: {
      ...Object.fromEntries(request.headers),
      "content-type": request.headers.get("content-type") ?? "",
    },
    body: request.body,
  });

  // Si 401 et pas de cookie de session mais un cookie de refresh existe,
  // tenter le renouvellement avant de rejeter.
  if (springResponse.status === 401) {
    const hasSession = cookieStore.get(SESSION_COOKIE)?.value;
    const hasRefresh = cookieStore.get("eparly_refresh")?.value;

    if (!hasSession && hasRefresh) {
      const refreshed = await ensureSessionOrRefresh();
      if (refreshed) {
        springResponse = await serverApiRequest(path, {
          method: request.method,
          headers: {
            ...Object.fromEntries(request.headers),
            Authorization: `Bearer ${refreshed.accessToken}`,
            "content-type": request.headers.get("content-type") ?? "",
          },
          body: request.body,
        });

        const finalResponse = forwardResponse(springResponse);
        finalResponse.cookies.set(SESSION_COOKIE, refreshed.accessToken, {
          ...sessionCookieOptions(refreshed.expiresInSeconds),
        });
        const sessionBinding =
          cookieStore.get(SESSION_BINDING_COOKIE)?.value ??
          randomBytes(32).toString("base64url");
        finalResponse.cookies.set(SESSION_BINDING_COOKIE, sessionBinding, {
          ...sessionCookieOptions(refreshed.refreshExpiresInSeconds),
        });
        finalResponse.cookies.set("eparly_csrf", generateCsrfToken(sessionBinding), {
          httpOnly: false,
          secure: process.env.NODE_ENV === "production",
          sameSite: "strict",
          path: "/",
          maxAge: refreshed.expiresInSeconds,
        });
        if (refreshed.newRefreshToken && refreshed.refreshExpiresInSeconds) {
          finalResponse.cookies.set("eparly_refresh", refreshed.newRefreshToken, {
            ...sessionCookieOptions(refreshed.refreshExpiresInSeconds),
          });
        }
        return finalResponse;
      }
    }

    // Refresh âchoué ou non applicable : session invalide
    return NextResponse.json(
      { error: "Session expirée. Veuillez vous reconnecter." },
      { status: 401 },
    );
  }

  return forwardResponse(springResponse);
}
