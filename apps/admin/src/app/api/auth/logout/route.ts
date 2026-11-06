import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { callBackend } from "@/lib/server/backend";
import { rejectUnsafeMutation } from "@/lib/server/request-guard";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session-cookies";

export async function POST(request: NextRequest) {
  const rejected = rejectUnsafeMutation(request);
  if (rejected) return rejected;

  const store = await cookies();
  const refreshToken = store.get(REFRESH_COOKIE)?.value;
  if (refreshToken) {
    // Révoque toute la famille de jetons ; l'API répond toujours 204.
    await callBackend("/v1/auth/logout", { method: "POST", body: { refreshToken }, accessToken: "" });
  }
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
  return NextResponse.json({ ok: true });
}
