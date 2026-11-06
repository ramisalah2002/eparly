const SESSION_COOKIE = "eparly_session";
const REFRESH_COOKIE = "eparly_refresh";
const CSRF_COOKIE = "eparly_csrf";
const SESSION_BINDING_COOKIE = "eparly_session_binding";

/**
 * Nettoie les anciens tokens stockés dans localStorage (migration vers cookies HttpOnly).
 * À appeler une fois au chargement de l'application.
 */
export function clearLegacyTokens(): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.removeItem("eparly_admin_access_token");
    localStorage.removeItem("eparly_admin_refresh_token");
    localStorage.removeItem("eparly_admin_token");
    sessionStorage.removeItem("eparly_admin_access_token");
    sessionStorage.removeItem("eparly_admin_refresh_token");
  } catch {
    // Ignorer les erreurs d'accâs au stockage
  }
}

/**
 * Lit le jeton CSRF depuis le cookie (cÃīté client).
 * Utilisé pour ajouter l'en-tÃ¢e X-CSRF-TOKEN sur les requâses mutantes.
 */
export function getCsrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(
    /(?:^|; )eparly_csrf=([^;]*)/
  );
  return match ? decodeURIComponent(match[1]) : null;
}

export interface ApiError {
  status: number;
  message: string;
  body?: unknown;
}

export class HttpError extends Error {
  public readonly status: number;
  public readonly body?: unknown;

  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = "HttpError";
    this.status = status;
    this.body = body;
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  const status = response.status;
  const contentType = response.headers.get("content-type") ?? "";
  let body: unknown = null;

  if (contentType.includes("application/json")) {
    try {
      body = await response.json();
    } catch {
      body = null;
    }
  } else {
    body = await response.text();
  }

  if (response.ok) {
    return body as T;
  }

  let message = `Erreur HTTP ${status}`;
  if (body && typeof body === "object" && "message" in body && typeof (body as { message: unknown }).message === "string") {
    message = (body as { message: string }).message;
  } else if (typeof body === "string" && body) {
    message = body;
  }

  throw new HttpError(status, message, body);
}

const HEADERS_TO_STRIP = new Set([
  "host",
  "content-length",
  "transfer-encoding",
  "connection",
  "upgrade",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
]);

export function filterForwardableHeaders(
  headers: Headers | Record<string, string> | undefined,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (!headers) return out;

  // Headers instance (from Request) or plain object (from RequestInit)
  if (typeof headers.forEach === "function") {
    (headers as Headers).forEach((value, key) => {
      if (!HEADERS_TO_STRIP.has(key.toLowerCase())) {
        out[key] = value;
      }
    });
  } else {
    for (const [key, value] of Object.entries(headers as Record<string, string>)) {
      if (!HEADERS_TO_STRIP.has(key.toLowerCase())) {
        out[key] = value;
      }
    }
  }
  return out;
}

export async function apiRequest<T>(
  input: string,
  init: RequestInit = {},
): Promise<T> {
  const csrfToken = getCsrfToken();
  const isMutating = ["POST", "PATCH", "PUT", "DELETE"].includes(
    (init.method ?? "GET").toUpperCase(),
  );

  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string> ?? {}),
  };

  // Ajouter le jeton CSRF sur les requâses mutantes
  if (isMutating && csrfToken) {
    headers["X-CSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(input, {
    ...init,
    headers,
    credentials: "include", // Envoyer les cookies HttpOnly
  });

  if (response.status === 401) {
    // Session expirée ou invalide
    if (typeof window !== "undefined" && window.location.pathname !== "/login") {
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.href = "/login";
    }
    throw new HttpError(
      401,
      "Authentification requise ou session expirée. Veuillez vous reconnecter.",
      await response.text(),
    );
  }

  if (response.status === 403) {
    throw new HttpError(
      403,
      "Accâs refusé : vous n'avez pas la permission CATALOG_ADMIN.",
      await response.text(),
    );
  }

  return handleResponse<T>(response);
}

export { SESSION_COOKIE, REFRESH_COOKIE, CSRF_COOKIE, SESSION_BINDING_COOKIE };
