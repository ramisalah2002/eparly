import { afterEach, describe, expect, it, vi } from "vitest";
import { csrfTokensMatch, resolveAdminRoute, sameOrigin } from "@/lib/bff-policy";
import { jwtExpiresAt, needsRefresh } from "@/lib/session-cookies";
import { refreshTokens } from "@/lib/server/token-refresh";

const OFFER = "11111111-1111-1111-1111-111111111111";
const EVIDENCE = "22222222-2222-2222-2222-222222222222";

describe("resolveAdminRoute", () => {
  it("autorise uniquement les mutations livrées", () => {
    expect(resolveAdminRoute("PATCH", ["catalog", "offers", OFFER, "evidences", EVIDENCE, "verification"])).toEqual({
      allowed: true,
      backendPath: `/v1/admin/catalog/offers/${OFFER}/evidences/${EVIDENCE}/verification`,
      publication: false,
    });
    expect(resolveAdminRoute("POST", ["catalog", "offers", OFFER, "publish"])).toMatchObject({ allowed: true, publication: true });
    expect(resolveAdminRoute("POST", ["catalog", "offers", OFFER, "archive"])).toMatchObject({ allowed: true, publication: false });
  });

  it("refuse les routes hors liste, les mauvaises méthodes et la traversée de chemin", () => {
    expect(resolveAdminRoute("POST", ["catalog", "providers"])).toEqual({ allowed: false, status: 404 });
    expect(resolveAdminRoute("PUT", ["catalog", "offers", OFFER, "publish"])).toEqual({ allowed: false, status: 405 });
    expect(resolveAdminRoute("POST", ["catalog", "offers", "..", "publish"])).toEqual({ allowed: false, status: 404 });
    expect(resolveAdminRoute("POST", ["catalog", "offers", "not-a-uuid", "publish"])).toEqual({ allowed: false, status: 404 });
    expect(resolveAdminRoute("POST", ["catalog-sync", "runs"])).toEqual({ allowed: false, status: 404 });
  });
});

describe("CSRF et origine", () => {
  const token = "a".repeat(64);

  it("exige un jeton identique au cookie", () => {
    expect(csrfTokensMatch(token, token)).toBe(true);
    expect(csrfTokensMatch(token, "b".repeat(64))).toBe(false);
    expect(csrfTokensMatch(token, null)).toBe(false);
    expect(csrfTokensMatch(undefined, token)).toBe(false);
    expect(csrfTokensMatch("court", "court")).toBe(false);
  });

  it("refuse une origine différente ou absente", () => {
    expect(sameOrigin("http://localhost:3000", "http://localhost:3000/api/admin/x")).toBe(true);
    expect(sameOrigin("http://evil.test", "http://localhost:3000/api/admin/x")).toBe(false);
    expect(sameOrigin(null, "http://localhost:3000/api/admin/x")).toBe(false);
  });
});

function jwt(payload: object): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "RS256" })}.${encode(payload)}.signature`;
}

describe("expiration du jeton d'accès", () => {
  it("lit exp et rafraîchit avant l'échéance", () => {
    const token = jwt({ exp: 1_000 });
    expect(jwtExpiresAt(token)).toBe(1_000);
    expect(needsRefresh(token, 900)).toBe(false);
    expect(needsRefresh(token, 980)).toBe(true);
    expect(needsRefresh(undefined, 0)).toBe(true);
    expect(needsRefresh("pas-un-jwt", 0)).toBe(true);
  });
});

describe("refreshTokens", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("mutualise les rafraîchissements concurrents d'un même jeton (pas de rejeu)", async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ accessToken: "new", refreshToken: "rt-2", expiresIn: 900, refreshExpiresIn: 100, tokenType: "Bearer" }), {
        status: 200,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const [a, b] = await Promise.all([refreshTokens("http://api", "rt-concurrent"), refreshTokens("http://api", "rt-concurrent")]);
    const c = await refreshTokens("http://api", "rt-concurrent");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(a?.accessToken).toBe("new");
    expect(b).toEqual(a);
    expect(c).toEqual(a);
  });

  it("renvoie null sur refus ou erreur réseau", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 401 })));
    expect(await refreshTokens("http://api", "rt-refused")).toBeNull();
    vi.stubGlobal("fetch", vi.fn(async () => {
      throw new Error("down");
    }));
    expect(await refreshTokens("http://api", "rt-down")).toBeNull();
  });
});
