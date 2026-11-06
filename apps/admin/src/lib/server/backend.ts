import "server-only";
import { cookies } from "next/headers";
import { ACCESS_COOKIE } from "@/lib/session-cookies";
import type { ApiErrorBody } from "@/lib/types";
import { apiBaseUrl } from "./config";

export type BackendResult<T> =
  | { ok: true; status: number; data: T }
  | { ok: false; status: number; error: ApiErrorBody | null };

interface CallOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  accessToken?: string;
}

/**
 * Appel brut vers Spring Boot avec le jeton d'accès de la session.
 * Le rafraîchissement est fait en amont par le proxy : ici, un 401 est une session expirée.
 * Status 0 = API injoignable.
 */
export async function callBackend<T>(path: string, options: CallOptions = {}): Promise<BackendResult<T>> {
  const token = options.accessToken ?? (await cookies()).get(ACCESS_COOKIE)?.value;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl()}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      cache: "no-store",
    });
  } catch {
    return { ok: false, status: 0, error: null };
  }

  const text = await response.text();
  let parsed: unknown = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null;
    }
  }
  if (response.ok) return { ok: true, status: response.status, data: parsed as T };
  return { ok: false, status: response.status, error: (parsed as ApiErrorBody) ?? null };
}
