import { NextResponse, type NextRequest } from "next/server";
import { apiBaseUrl, isProduction } from "@/lib/server/config";
import { refreshTokens } from "@/lib/server/token-refresh";
import {
  ACCESS_COOKIE,
  CSRF_COOKIE,
  csrfCookieOptions,
  needsRefresh,
  REFRESH_COOKIE,
  sessionCookieOptions,
} from "@/lib/session-cookies";

const PUBLIC_PATHS = new Set(["/login", "/api/auth/login"]);

function newCsrfToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function unauthenticated(request: NextRequest): NextResponse {
  const { pathname, search } = request.nextUrl;
  const response = pathname.startsWith("/api/")
    ? NextResponse.json({ status: 401, message: "Session expirée" }, { status: 401 })
    : NextResponse.redirect(
        new URL(`/login?next=${encodeURIComponent(pathname + search)}`, request.url),
      );
  response.cookies.delete(ACCESS_COOKIE);
  response.cookies.delete(REFRESH_COOKIE);
  return response;
}

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const csrf = request.cookies.get(CSRF_COOKIE)?.value ?? null;
  const freshCsrf = csrf ? null : newCsrfToken();

  const withCsrf = (response: NextResponse) => {
    if (freshCsrf) response.cookies.set(CSRF_COOKIE, freshCsrf, csrfCookieOptions(isProduction));
    return response;
  };

  if (PUBLIC_PATHS.has(pathname)) {
    return withCsrf(NextResponse.next());
  }

  const access = request.cookies.get(ACCESS_COOKIE)?.value;
  const refresh = request.cookies.get(REFRESH_COOKIE)?.value;
  const nowSeconds = Math.floor(Date.now() / 1000);

  if (!needsRefresh(access, nowSeconds)) {
    return withCsrf(NextResponse.next());
  }
  if (!refresh) {
    return unauthenticated(request);
  }

  const tokens = await refreshTokens(apiBaseUrl(), refresh);
  if (!tokens) {
    return unauthenticated(request);
  }

  // Le rendu de cette requête doit déjà voir les nouveaux jetons.
  request.cookies.set(ACCESS_COOKIE, tokens.accessToken);
  request.cookies.set(REFRESH_COOKIE, tokens.refreshToken);
  const response = NextResponse.next({ request: { headers: request.headers } });
  response.cookies.set(ACCESS_COOKIE, tokens.accessToken, sessionCookieOptions(isProduction, tokens.expiresIn));
  response.cookies.set(
    REFRESH_COOKIE,
    tokens.refreshToken,
    sessionCookieOptions(isProduction, tokens.refreshExpiresIn),
  );
  return withCsrf(response);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)"],
};
