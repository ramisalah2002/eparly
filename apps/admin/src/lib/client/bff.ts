"use client";

import { describeApiError } from "@/lib/format";
import { CSRF_COOKIE, CSRF_HEADER } from "@/lib/session-cookies";

function readCsrfToken(): string {
  const match = document.cookie.split("; ").find((part) => part.startsWith(`${CSRF_COOKIE}=`));
  return match ? decodeURIComponent(match.slice(CSRF_COOKIE.length + 1)) : "";
}

export type MutationResult<T> = { ok: true; data: T | null } | { ok: false; status: number; message: string };

/** Mutation via le BFF : cookie de session HttpOnly + jeton CSRF en en-tête. */
export async function bffMutation<T>(
  url: string,
  method: "POST" | "PUT" | "PATCH",
  body?: unknown,
): Promise<MutationResult<T>> {
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        [CSRF_HEADER]: readCsrfToken(),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    return { ok: false, status: 0, message: describeApiError(0, null) };
  }

  let payload: unknown = null;
  if (response.status !== 204) {
    try {
      payload = await response.json();
    } catch {
      payload = null;
    }
  }
  if (response.ok) return { ok: true, data: payload as T };
  const error = payload as { message?: string | null; localized?: boolean } | null;
  const message = error?.message ?? null;
  return {
    ok: false,
    status: response.status,
    message: error?.localized && message ? message : describeApiError(response.status, message),
  };
}
