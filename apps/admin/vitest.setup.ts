import { vi } from "vitest";

// Variables d'environnement pour les tests
process.env.NEXT_PUBLIC_API_BASE = "http://localhost:8080";
process.env.API_BASE = "http://localhost:8080";
vi.stubEnv("NODE_ENV", "test");
process.env.CSRF_SECRET = "test-csrf-secret";

// Mocks DOM minimaux pour les tests qui utilisent document.cookie / localStorage / sessionStorage
const createStorageMock = () => {
  const store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      Object.keys(store).forEach((key) => delete store[key]);
    },
  };
};

const csrfCookieStore: Record<string, string> = {};

Object.defineProperty(globalThis, "document", {
  value: {
    get cookie() {
      return Object.entries(csrfCookieStore)
        .map(([k, v]) => `${k}=${v}`)
        .join("; ");
    },
    set cookie(value: string) {
      const match = value.match(/([^=]+)=([^;]*)/);
      if (match) {
        csrfCookieStore[match[1]] = match[2];
      }
    },
  },
  writable: true,
});

Object.defineProperty(globalThis, "localStorage", {
  value: createStorageMock(),
  writable: true,
});

Object.defineProperty(globalThis, "sessionStorage", {
  value: createStorageMock(),
  writable: true,
});

Object.defineProperty(globalThis, "window", {
  value: {
    location: {
      pathname: "/",
      href: "http://localhost:3000/",
    },
  },
  writable: true,
});
const originalError = console.error;
console.error = (...args: unknown[]) => {
  if (
    args.some(
      (a) =>
        typeof a === "string" &&
        (a.includes("Warning:") || a.includes("ReactDOM")),
    )
  ) {
    return;
  }
  originalError.apply(console, args);
};
