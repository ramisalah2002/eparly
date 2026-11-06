import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  refreshSession,
  ensureSessionOrRefresh,
  serverApiRequest,
} from "@/lib/server-api";
import { cookies } from "next/headers";

const mockCookiesImpl = vi.mocked(cookies);

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

let mockCookies: Map<string, { value: string }>;

beforeEach(() => {
  mockCookies = new Map();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  mockCookiesImpl.mockReturnValue(mockCookies as any);
  vi.stubEnv("API_BASE", "http://localhost:8080");
  vi.stubEnv("CSRF_SECRET", "test-csrf-secret-with-at-least-32-bytes");
  vi.stubEnv("NODE_ENV", "test");
});

afterEach(() => {
  vi.unstubAllEnvs();
  mockCookies.clear();
  vi.restoreAllMocks();
});

function setCookies(
  session: string | null,
  refresh: string | null,
): void {
  if (session) mockCookies.set("eparly_session", { value: session });
  else mockCookies.delete("eparly_session");
  if (refresh) mockCookies.set("eparly_refresh", { value: refresh });
  else mockCookies.delete("eparly_refresh");
}

function mockFetch(response: Response): void {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve(response)) as unknown as typeof fetch,
  );
}

describe("session renewal when access cookie expired", () => {
  it("renouvelle la session quand session absente mais refresh présent", async () => {
    setCookies(null, "valid-refresh-token");

    mockFetch(
      new Response(
        JSON.stringify({
          accessToken: "new-access-token",
          refreshToken: "new-refresh-token",
          expiresIn: 900,
          refreshExpiresIn: 2592000,
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const result = await ensureSessionOrRefresh();
    expect(result).not.toBeNull();
    expect(result?.accessToken).toBe("new-access-token");
    expect(result?.expiresInSeconds).toBe(900);
    expect(result?.newRefreshToken).toBe("new-refresh-token");
  });

  it("ne renouvelle pas quand session présente", async () => {
    setCookies("valid-session", "valid-refresh-token");

    const result = await ensureSessionOrRefresh();
    expect(result).toBeNull();
  });

  it("retourne null quand aucun cookie présent", async () => {
    setCookies(null, null);

    const result = await ensureSessionOrRefresh();
    expect(result).toBeNull();
  });

  it("retourne null quand refresh invalide (401 Spring Boot)", async () => {
    setCookies(null, "expired-refresh-token");

    mockFetch(
      new Response(
        JSON.stringify({ error: "Refresh token is invalid or expired" }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const result = await ensureSessionOrRefresh();
    expect(result).toBeNull();
  });
});

describe("concurrent refresh token rotation", () => {
  it("retourne le même résultat pour deux appels concurrents avec le même token", async () => {
    setCookies(null, "shared-refresh-token");

    mockFetch(
      new Response(
        JSON.stringify({
          accessToken: "new-access-token",
          refreshToken: "new-refresh-token",
          expiresIn: 900,
          refreshExpiresIn: 2592000,
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const p1 = refreshSession();
    const p2 = refreshSession();

    const [r1, r2] = await Promise.all([p1, p2]);

    // Même verrou : même résultat, un seul appel effectif à Spring Boot
    expect(r1).toEqual(r2);
    expect(r1?.accessToken).toBe("new-access-token");
    expect(r1?.newRefreshToken).toBe("new-refresh-token");
  });
});

describe("serverApiRequest", () => {
  it("transmet le bearer token depuis le cookie de session", async () => {
    setCookies("session-jwt-value", null);

    mockFetch(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await serverApiRequest("/v1/test", { method: "GET" });

    const callArgs = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = callArgs[1]?.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe("Bearer session-jwt-value");
  });

  it("n'ajoute pas d'Authorization quand pas de session", async () => {
    setCookies(null, null);

    mockFetch(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await serverApiRequest("/v1/test", { method: "GET" });

    const callArgs = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = callArgs[1]?.headers as Record<string, string>;
    expect(headers["Authorization"]).toBeUndefined();
  });
});
