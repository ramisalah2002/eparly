import { beforeEach, describe, expect, it, vi } from "vitest";
import { cookies } from "next/headers";
import { generateCsrfToken, verifyCsrfToken } from "@/lib/csrf";
import { proxiedRequest } from "@/lib/proxy-handler";
import { ensureSessionOrRefresh, serverApiRequest } from "@/lib/server-api";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/server-api", () => ({
  ensureSessionOrRefresh: vi.fn(),
  serverApiRequest: vi.fn(),
  sessionCookieOptions: (maxAge?: number) => ({
    httpOnly: true,
    ...(maxAge == null ? {} : { maxAge }),
  }),
}));

const cookieStore = vi.mocked(cookies);
const refreshSession = vi.mocked(ensureSessionOrRefresh);
const requestSpring = vi.mocked(serverApiRequest);

beforeEach(() => {
  process.env.CSRF_SECRET = "test-csrf-secret-with-at-least-32-bytes";
  cookieStore.mockResolvedValue(new Map([
    ["eparly_refresh", { value: "refresh-cookie" }],
    ["eparly_session_binding", { value: "stable-session-binding" }],
  ]) as never);
  refreshSession.mockResolvedValue({
    accessToken: "rotated-access-jwt",
    expiresInSeconds: 900,
    newRefreshToken: "rotated-refresh-token",
    refreshExpiresInSeconds: 86_400,
  });
  requestSpring
    .mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(new Response("{}", { status: 200 }));
});

describe("CSRF continuity during session refresh", () => {
  it("issues a fresh CSRF token bound to the stable session after JWT rotation", async () => {
    const oldToken = generateCsrfToken("stable-session-binding");
    const response = await proxiedRequest(
      new Request("http://localhost:3000/api/admin/catalog/providers"),
      "/v1/admin/catalog/providers",
      ["GET"],
    );

    const newToken = response.cookies.get("eparly_csrf")?.value;
    expect(response.status).toBe(200);
    expect(newToken).toBeTruthy();
    expect(newToken).not.toBe(oldToken);
    expect(verifyCsrfToken(newToken ?? null, "stable-session-binding")).toBe(true);
    expect(newToken).not.toContain("rotated-access-jwt");
    expect(response.cookies.get("eparly_session")?.value).toBe("rotated-access-jwt");
    expect(response.cookies.get("eparly_session_binding")?.httpOnly).toBe(true);
  });
});
