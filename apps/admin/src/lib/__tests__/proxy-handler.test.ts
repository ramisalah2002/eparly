import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { verifyRequestCsrf } from "@/lib/proxy-handler";
import { generateCsrfToken } from "@/lib/csrf";

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
  process.env.CSRF_SECRET = "test-csrf-secret-with-at-least-32-bytes";
});

afterEach(() => {
  delete process.env.CSRF_SECRET;
  mockCookies.clear();
});

function createRequest(
  method: string,
  url: string,
  headers: Record<string, string> = {},
): Request {
  return new Request(url, {
    method,
    headers: {
      ...headers,
      host: "localhost:3000",
    },
  });
}

describe("verifyRequestCsrf", () => {
  it("autorise les requêtes GET sans CSRF", () => {
    const req = createRequest("GET", "http://localhost:3000/api/test");
    expect(verifyRequestCsrf(req)).toBe(true);
  });

  it("autorise le login sans session (pas de CSRF requis)", () => {
    const req = createRequest("POST", "http://localhost:3000/api/auth/login");
    expect(verifyRequestCsrf(req)).toBe(true);
  });

  it("rejette une requête mutante sans jeton CSRF", () => {
    const req = createRequest("POST", "http://localhost:3000/api/admin/catalog/offers");
    expect(verifyRequestCsrf(req)).toBe(false);
  });

  it("rejette une requête mutante avec jeton CSRF invalide", () => {
    const req = createRequest("POST", "http://localhost:3000/api/admin/catalog/offers", {
      "X-CSRF-TOKEN": "invalid-token",
    });
    expect(verifyRequestCsrf(req)).toBe(false);
  });

  it("accepte une requête mutante avec jeton CSRF valide", () => {
    const sessionBinding = "session-binding-one";
    const token = generateCsrfToken(sessionBinding);
    const req = createRequest("POST", "http://localhost:3000/api/admin/catalog/offers", {
      "X-CSRF-TOKEN": token,
      Cookie: `eparly_session_binding=${sessionBinding}`,
    });
    expect(verifyRequestCsrf(req)).toBe(true);
  });

  it("rejette un jeton CSRF émis pour une autre session", () => {
    const token = generateCsrfToken("session-binding-one");
    const req = createRequest("POST", "http://localhost:3000/api/admin/catalog/offers", {
      "X-CSRF-TOKEN": token,
      Cookie: "eparly_session_binding=session-binding-two",
    });
    expect(verifyRequestCsrf(req)).toBe(false);
  });

  it("rejette une requête avec origine non autorisée", () => {
    const sessionBinding = "session-binding-one";
    const token = generateCsrfToken(sessionBinding);
    const req = createRequest("POST", "http://localhost:3000/api/admin/catalog/offers", {
      "X-CSRF-TOKEN": token,
      Cookie: `eparly_session_binding=${sessionBinding}`,
      Origin: "http://evil.com",
    });
    expect(verifyRequestCsrf(req)).toBe(false);
  });

  it("rejette une origine qui ne correspond qu'au préfixe de l'hôte", () => {
    const sessionBinding = "session-binding-one";
    const token = generateCsrfToken(sessionBinding);
    const req = createRequest("POST", "http://localhost:3000/api/admin/catalog/offers", {
      "X-CSRF-TOKEN": token,
      Cookie: `eparly_session_binding=${sessionBinding}`,
      Referer: "http://localhost:3000.evil.example/path",
    });
    expect(verifyRequestCsrf(req)).toBe(false);
  });
});
