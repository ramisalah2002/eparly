import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const SESSION_COOKIE = "eparly_session";

/**
 * Next.js 16 proxy (replaces deprecated middleware).
 * Protège les routes administratives en vérifiant la présence du cookie
 * de session HttpOnly. L'autorisation CATALOG_ADMIN reste vérifiée par
 * Spring Boot sur chaque appel API.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const sessionCookie = request.cookies.get(SESSION_COOKIE);

  // Routes publiques : aucun cookie requis
  if (
    pathname === "/" ||
    pathname === "/login" ||
    pathname.startsWith("/api/") ||
    pathname.startsWith("/_next/") ||
    pathname.startsWith("/favicon.ico")
  ) {
    return NextResponse.next();
  }

  // Routes admin protégées : redirection vers /login si pas de session
  if (pathname.startsWith("/catalog") || pathname === "/dashboard") {
    if (!sessionCookie) {
      const loginUrl = new URL("/login", request.url);
      loginUrl.searchParams.set("callback", pathname);
      return NextResponse.redirect(loginUrl);
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
