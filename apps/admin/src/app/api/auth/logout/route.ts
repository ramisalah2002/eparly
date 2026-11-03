import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { serverApiRequest, sessionCookieOptions } from "@/lib/server-api";
import { REFRESH_COOKIE, SESSION_COOKIE, SESSION_BINDING_COOKIE } from "@/lib/api";

/**
 * POST /api/auth/logout
 * DÉconnexion : rÉvoque la session cÃīté Spring Boot et efface les cookies.
 */
export async function POST() {
  try {
    // RÉcupÉrer le refresh token pour rÉvoquer cÃīté Spring Boot
    const cookieStore = await cookies();
    const refreshToken = cookieStore.get(REFRESH_COOKIE)?.value;

    if (refreshToken) {
      // Tenter la dÉconnexion cÃīté Spring Boot (best effort)
      await serverApiRequest("/v1/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
    }
  } catch {
    // Ignorer : le nettoyage cÃīté client est prioritaire
  }

  const response = NextResponse.json(
    { success: true, message: "Déconnecté" },
    { status: 200 },
  );

  // Effacer tous les cookies de session
  response.cookies.set(SESSION_COOKIE, "", {
    ...sessionCookieOptions(),
    maxAge: 0,
    path: "/",
  });
  response.cookies.set(REFRESH_COOKIE, "", {
    ...sessionCookieOptions(),
    maxAge: 0,
    path: "/",
  });
  response.cookies.set(SESSION_BINDING_COOKIE, "", {
    ...sessionCookieOptions(),
    maxAge: 0,
    path: "/",
  });
  response.cookies.set("eparly_csrf", "", {
    httpOnly: false,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 0,
  });

  return response;
}
