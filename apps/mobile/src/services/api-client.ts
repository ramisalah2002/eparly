import {
  clearAuthSession,
  getAccessToken,
  getRefreshToken,
  saveAuthTokens,
} from './session-storage';

export interface ApiClientConfig {
  getBaseUrl: () => string;
  onSessionExpired?: () => void;
}

let activeBaseUrlGetter: () => string = () => 'http://localhost:8080';
let sessionExpiredCallback: (() => void) | null = null;

// Module-level in-flight refresh promise for single-flight token rotation
let inFlightRefreshPromise: Promise<string | null> | null = null;
let isLoggingOut = false;

export function configureApiClient(config: ApiClientConfig): void {
  activeBaseUrlGetter = config.getBaseUrl;
  if (config.onSessionExpired) {
    sessionExpiredCallback = config.onSessionExpired;
  }
}

export function setLoggingOut(loggingOut: boolean): void {
  isLoggingOut = loggingOut;
  if (loggingOut) {
    inFlightRefreshPromise = null;
  }
}

/**
 * Executes a single-flight token refresh against the backend /v1/auth/refresh endpoint.
 * Concurrent calls will share the same Promise to prevent token-rotation race conditions
 * and accidental token family revocations.
 */
export async function executeSingleFlightRefresh(): Promise<string | null> {
  if (isLoggingOut) {
    return null;
  }

  if (inFlightRefreshPromise) {
    return inFlightRefreshPromise;
  }

  inFlightRefreshPromise = (async () => {
    try {
      const storedRefreshToken = await getRefreshToken();
      if (!storedRefreshToken || isLoggingOut) {
        await clearAuthSession();
        sessionExpiredCallback?.();
        return null;
      }

      const baseUrl = activeBaseUrlGetter();
      const endpoint = `${baseUrl}/v1/auth/refresh`;

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({ refreshToken: storedRefreshToken }),
      });

      if (isLoggingOut) {
        return null;
      }

      if (response.ok) {
        const data = (await response.json()) as {
          accessToken: string;
          refreshToken: string;
        };

        if (data.accessToken && data.refreshToken) {
          await saveAuthTokens(data.accessToken, data.refreshToken);
          return data.accessToken;
        }
      }

      // 401/400 or invalid payload indicates token rejection or revocation
      if (response.status === 401 || response.status === 400 || response.status === 403) {
        await clearAuthSession();
        sessionExpiredCallback?.();
        return null;
      }

      // Server error or unexpected response: do not clear session on transient server errors
      return null;
    } catch {
      // Network failure: preserve session for offline handling
      return null;
    } finally {
      inFlightRefreshPromise = null;
    }
  })();

  return inFlightRefreshPromise;
}

export interface AuthenticatedRequestInit extends RequestInit {
  _isRetry?: boolean;
  skipAuth?: boolean;
}

/**
 * Authenticated fetch client with automatic single-flight token rotation and single retry on 401.
 */
export async function authenticatedFetch(
  pathOrUrl: string,
  init: AuthenticatedRequestInit = {}
): Promise<Response> {
  const baseUrl = activeBaseUrlGetter();
  const url = pathOrUrl.startsWith('http://') || pathOrUrl.startsWith('https://')
    ? pathOrUrl
    : `${baseUrl}${pathOrUrl.startsWith('/') ? pathOrUrl : `/${pathOrUrl}`}`;

  const headers = new Headers(init.headers || {});
  headers.set('Accept', 'application/json');

  if (!init.skipAuth) {
    const accessToken = await getAccessToken();
    if (accessToken && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${accessToken}`);
    }
  }

  const response = await fetch(url, {
    ...init,
    headers,
  });

  const isAuthEndpoint =
    url.includes('/v1/auth/login') ||
    url.includes('/v1/auth/refresh') ||
    url.includes('/v1/auth/register') ||
    url.includes('/v1/auth/forgot-password') ||
    url.includes('/v1/auth/verify-reset-code') ||
    url.includes('/v1/auth/reset-password');

  // Trigger token refresh only on 401 for authenticated endpoints and only once
  if (response.status === 401 && !isAuthEndpoint && !init._isRetry && !init.skipAuth) {
    const newAccessToken = await executeSingleFlightRefresh();

    if (newAccessToken && !isLoggingOut) {
      const retryHeaders = new Headers(init.headers || {});
      retryHeaders.set('Accept', 'application/json');
      retryHeaders.set('Authorization', `Bearer ${newAccessToken}`);

      return fetch(url, {
        ...init,
        headers: retryHeaders,
        _isRetry: true,
      } as RequestInit);
    }
  }

  return response;
}
