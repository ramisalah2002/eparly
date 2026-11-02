import { NextResponse } from "next/server";
import { serverApiRequest } from "@/lib/server-api";

/**
 * GET /api/auth/me
 * VÉrifie la validité de la session et renvoie les informations utilisateur.
 * Utilisé par le cÃīté client pour confirmer l'État d'authentification.
 */
export async function GET() {
  try {
    const springResponse = await serverApiRequest("/v1/me", {
      method: "GET",
    });

    if (!springResponse.ok) {
      return NextResponse.json(
        { authenticated: false },
        { status: springResponse.status },
      );
    }

    const data = await springResponse.json();
    return NextResponse.json({ authenticated: true, user: data }, { status: 200 });
  } catch {
    return NextResponse.json(
      { authenticated: false },
      { status: 401 },
    );
  }
}
