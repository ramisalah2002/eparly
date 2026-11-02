import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  apiRequest,
  clearLegacyTokens,
  getCsrfToken,
} from "@/lib/api";

const originalFetch = global.fetch;

beforeEach(() => {
  Object.defineProperty(document, "cookie", {
    value: "eparly_csrf=test-csrf-token",
    configurable: true,
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  global.fetch = originalFetch;
});

function mockFetch(response: Response): void {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve(response)) as unknown as typeof fetch,
  );
}

describe("apiRequest", () => {
  it("effectue une requête GET réussie et retourne les données", async () => {
    const mockResponse = { items: [{ id: "1", name: "Test" }] };
    mockFetch(
      new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const result = await apiRequest<{
      items: Array<{ id: string; name: string }>;
    }>("/api/test");
    expect(result).toEqual(mockResponse);
    expect(global.fetch).toHaveBeenCalled();
    const callArgs = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(callArgs[0]).toContain("/api/test");
    expect((callArgs[1] as RequestInit).credentials).toBe("include");
  });

  it("lance une HttpError avec le statut 401", async () => {
    mockFetch(
      new Response(JSON.stringify({ message: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(apiRequest("/api/test")).rejects.toMatchObject({
      status: 401,
      name: "HttpError",
    });
  });

  it("lance une HttpError avec le statut 403", async () => {
    mockFetch(
      new Response(JSON.stringify({ message: "Forbidden" }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(apiRequest("/api/test")).rejects.toMatchObject({
      status: 403,
      name: "HttpError",
    });
  });

  it("ajoute l'en-tête CSRF sur les requêtes mutantes", async () => {
    mockFetch(new Response("{}", { status: 200 }));

    await apiRequest("/api/test", {
      method: "POST",
      body: JSON.stringify({ foo: "bar" }),
    });

    const callArgs = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = callArgs[1]?.headers as Record<string, string>;
    expect(headers["X-CSRF-TOKEN"]).toBe("test-csrf-token");
  });

  it("n'ajoute pas d'en-tête CSRF sur les requêtes GET", async () => {
    mockFetch(new Response("{}", { status: 200 }));

    await apiRequest("/api/test", { method: "GET" });

    const callArgs = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = callArgs[1]?.headers as Record<string, string>;
    expect(headers["X-CSRF-TOKEN"]).toBeUndefined();
  });

  it("utilise credentials: include pour envoyer les cookies", async () => {
    mockFetch(new Response("{}", { status: 200 }));

    await apiRequest("/api/test");

    const callArgs = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    const init = callArgs[1] as RequestInit;
    expect(init.credentials).toBe("include");
  });
});

describe("clearLegacyTokens", () => {
  it("supprime les anciens tokens de localStorage et sessionStorage", () => {
    localStorage.setItem("eparly_admin_access_token", "old-token");
    localStorage.setItem("eparly_admin_refresh_token", "old-refresh");
    sessionStorage.setItem("eparly_admin_access_token", "old-token");

    clearLegacyTokens();

    expect(localStorage.getItem("eparly_admin_access_token")).toBeNull();
    expect(localStorage.getItem("eparly_admin_refresh_token")).toBeNull();
    expect(sessionStorage.getItem("eparly_admin_access_token")).toBeNull();
  });
});

describe("getCsrfToken", () => {
  it("lit le jeton CSRF depuis document.cookie", () => {
    Object.defineProperty(document, "cookie", {
      value: "eparly_csrf=my-csrf-token; other=cookie",
      configurable: true,
    });
    expect(getCsrfToken()).toBe("my-csrf-token");
  });

  it("retourne null si aucun jeton CSRF", () => {
    Object.defineProperty(document, "cookie", {
      value: "other=cookie",
      configurable: true,
    });
    expect(getCsrfToken()).toBeNull();
  });
});
