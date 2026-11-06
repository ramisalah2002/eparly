import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { generateCsrfToken, verifyCsrfToken } from "@/lib/csrf";

beforeEach(() => {
  process.env.CSRF_SECRET = "test-csrf-secret-with-at-least-32-bytes";
});

afterEach(() => {
  delete process.env.CSRF_SECRET;
});

describe("CSRF signed token (no sensitive data)", () => {
  it("génère et vérifie un jeton valide", () => {
    const token = generateCsrfToken("session-binding-one");
    expect(typeof token).toBe("string");
    // Format : timestamp:nonce:hex_signature (3 parties séparées par :)
    expect(token.split(":").length).toBe(3);
    expect(verifyCsrfToken(token, "session-binding-one")).toBe(true);
    expect(verifyCsrfToken(token, "session-binding-two")).toBe(false);
  });

  it("rejette un jeton nul", () => {
    expect(verifyCsrfToken(null, "session-binding-one")).toBe(false);
    expect(verifyCsrfToken("", "session-binding-one")).toBe(false);
    expect(verifyCsrfToken(null as unknown as string, "session-binding-one")).toBe(false);
  });

  it("rejette un jeton malformé", () => {
    expect(verifyCsrfToken("not-a-token", "session-binding-one")).toBe(false);
    expect(verifyCsrfToken("onlyonepart", "session-binding-one")).toBe(false);
    expect(verifyCsrfToken("a:b:c:d", "session-binding-one")).toBe(false);
  });

  it("rejette un jeton avec signature invalide", () => {
    const validToken = generateCsrfToken("session-binding-one");
    const parts = validToken.split(":");
    const tampered = `${parts[0]}:${parts[1]}:invalidsignature`;
    expect(verifyCsrfToken(tampered, "session-binding-one")).toBe(false);
  });

  it("régénère un jeton tout en conservant la liaison après rotation JWT", () => {
    const t1 = generateCsrfToken("session-binding-one");
    const t2 = generateCsrfToken("session-binding-one");
    expect(t1).not.toBe(t2);
    expect(verifyCsrfToken(t1, "session-binding-one")).toBe(true);
    expect(verifyCsrfToken(t2, "session-binding-one")).toBe(true);
  });

  it("ne contient aucun JWT dans le token", () => {
    const fakeJwt =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    const token = generateCsrfToken("session-binding-one");

    // Vérifier que le JWT n'apparaît pas dans le token CSRF
    expect(token).not.toContain(fakeJwt);
    expect(token).not.toContain("eyJhbGci");
    expect(token).not.toContain("sub");
    expect(token).not.toContain("name");

    // Vérifier le format : timestamp:nonce:signature
    const [ts, nonce, sig] = token.split(":");
    expect(Number.isFinite(Number(ts))).toBe(true);
    expect(nonce.length).toBeGreaterThan(0);
    expect(sig.length).toBe(64); // HMAC-SHA256 hex = 64 chars
  });

  it("refuse de générer ou vérifier un jeton sans secret serveur fort", () => {
    process.env.CSRF_SECRET = "short";
    expect(() => generateCsrfToken("session-binding-one")).toThrow(/CSRF_SECRET/);
    expect(() => verifyCsrfToken("1:nonce:signature", "session-binding-one")).toThrow(/CSRF_SECRET/);
    delete process.env.CSRF_SECRET;
    expect(() => generateCsrfToken("session-binding-one")).toThrow(/CSRF_SECRET/);
  });

  it("refuse de vérifier un jeton sans identifiant de session", () => {
    const token = generateCsrfToken("session-binding-one");
    expect(verifyCsrfToken(token, null)).toBe(false);
  });
});
