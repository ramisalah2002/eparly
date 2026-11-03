import { NextResponse } from "next/server";
import { randomBytes } from "crypto";
import { serverApiRequest, sessionCookieOptions } from "@/lib/server-api";
import { SESSION_COOKIE, REFRESH_COOKIE, SESSION_BINDING_COOKIE } from "@/lib/api";
import { generateCsrfToken } from "@/lib/csrf";

/**
 * POST /api/auth/login
 * Proxy de connexion vers Spring Boot.
 * Les tokens sont stockés dans des cookies HttpOnly, jamais exposés au JavaScript.
 * La durÉe des cookies est alignÉe sur le véritable TTL des jetons retournés par Spring Boot.
 * Le jeton CSRF est signé pour un identifiant de session opaque et HttpOnly.
 */
export async function POST(request: Request) {
  try {
    const body = await request.json();

    const springResponse = await serverApiRequest("/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    if (!springResponse.ok) {
      return NextResponse.json(
        { error: "Identifiants invalides" },
        { status: springResponse.status },
      );
    }

    const data = await springResponse.json();
    const accessToken = data.accessToken as string | undefined;
    const refreshToken = data.refreshToken as string | undefined;
    const expiresIn = data.expiresIn as number | undefined;
    const refreshExpiresIn = data.refreshExpiresIn as number | undefined;

    if (!accessToken || !refreshToken) {
      return NextResponse.json(
        { error: "Réponse d'authentification incomplâte" },
        { status: 502 },
      );
    }

    const response = NextResponse.json(
      { success: true, message: "Connexion réussie" },
      { status: 200 },
    );

    // Cookie session (access token) - HttpOnly, Secure, SameSite=Strict
    // DurÉe alignÉe sur le TTL rÉel du JWT (15 min par dÉfaut)
    response.cookies.set(SESSION_COOKIE, accessToken, {
      ...sessionCookieOptions(expiresIn),
    });

    // Cookie refresh - HttpOnly, Secure, SameSite=Strict
    response.cookies.set(REFRESH_COOKIE, refreshToken, {
      ...sessionCookieOptions(refreshExpiresIn),
    });

    // Jeton CSRF signé cÃīté serveur, non liÉ Ã la valeur du JWT.
    // Non HttpOnly pour âtre lisible cÃīté client (en-tâe X-CSRF-TOKEN)
    const sessionBinding = randomBytes(32).toString("base64url");
    const csrfToken = generateCsrfToken(sessionBinding);
    response.cookies.set(SESSION_BINDING_COOKIE, sessionBinding, {
      ...sessionCookieOptions(refreshExpiresIn),
    });
    response.cookies.set("eparly_csrf", csrfToken, {
      httpOnly: false,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: expiresIn ?? 60 * 15,
    });

    return response;
  } catch {
    return NextResponse.json(
      { error: "Erreur interne lors de l'authentification" },
      { status: 500 },
    );
  }
}
