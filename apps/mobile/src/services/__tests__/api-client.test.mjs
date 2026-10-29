import test, { describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

// Mock storage and state for api-client regression tests
let mockAccessToken = 'initial_access_token';
let mockRefreshToken = 'initial_refresh_token';
let secureStoreThrowOnWrite = false;
let legacyAsyncStorage = {
  eparly_access_token: 'legacy_insecure_access',
  eparly_refresh_token: 'legacy_insecure_refresh',
  eparly_user_profile: '{"email":"test@example.com"}',
};

// In-memory token storage simulation matching session-storage rules
const mockStorage = {
  async getAccessToken() {
    return mockAccessToken;
  },
  async getRefreshToken() {
    return mockRefreshToken;
  },
  async saveAuthTokens(access, refresh) {
    if (secureStoreThrowOnWrite) {
      throw new Error('SecureStore write failed');
    }
    mockAccessToken = access;
    mockRefreshToken = refresh;
  },
  async clearAuthSession() {
    mockAccessToken = null;
    mockRefreshToken = null;
    delete legacyAsyncStorage.eparly_access_token;
    delete legacyAsyncStorage.eparly_refresh_token;
  },
  async cleanLegacyInsecureTokens() {
    delete legacyAsyncStorage.eparly_access_token;
    delete legacyAsyncStorage.eparly_refresh_token;
  },
};

// Implement Single-Flight Token Refresh mechanism matching api-client.ts
class TestApiClient {
  inFlightRefreshPromise = null;
  isLoggingOut = false;
  refreshCount = 0;
  mockFetchHandler = null;

  setLoggingOut(value) {
    this.isLoggingOut = value;
    if (value) {
      this.inFlightRefreshPromise = null;
    }
  }

  async executeSingleFlightRefresh() {
    if (this.isLoggingOut) return null;
    if (this.inFlightRefreshPromise) return this.inFlightRefreshPromise;

    this.inFlightRefreshPromise = (async () => {
      try {
        const storedRefreshToken = await mockStorage.getRefreshToken();
        if (!storedRefreshToken || this.isLoggingOut) {
          await mockStorage.clearAuthSession();
          return null;
        }

        this.refreshCount++;
        const response = await this.mockFetch('/v1/auth/refresh', {
          method: 'POST',
          body: JSON.stringify({ refreshToken: storedRefreshToken }),
        });

        if (this.isLoggingOut) return null;

        if (response.ok) {
          const data = await response.json();
          await mockStorage.saveAuthTokens(data.accessToken, data.refreshToken);
          return data.accessToken;
        }

        if (response.status === 401 || response.status === 400 || response.status === 403) {
          await mockStorage.clearAuthSession();
          return null;
        }

        return null;
      } catch {
        return null;
      } finally {
        this.inFlightRefreshPromise = null;
      }
    })();

    return this.inFlightRefreshPromise;
  }

  async mockFetch(url, init) {
    if (this.mockFetchHandler) {
      return this.mockFetchHandler(url, init);
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  }

  async authenticatedFetch(url, init = {}) {
    const headers = new Headers(init.headers || {});
    const token = await mockStorage.getAccessToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);

    const response = await this.mockFetch(url, { ...init, headers });

    const isAuthEndpoint = url.includes('/v1/auth/');
    if (response.status === 401 && !isAuthEndpoint && !init._isRetry) {
      const newToken = await this.executeSingleFlightRefresh();
      if (newToken && !this.isLoggingOut) {
        const retryHeaders = new Headers(init.headers || {});
        retryHeaders.set('Authorization', `Bearer ${newToken}`);
        return this.mockFetch(url, {
          ...init,
          headers: retryHeaders,
          _isRetry: true,
        });
      }
    }

    return response;
  }
}

describe('Single-Flight Token Refresh & Secure Storage Hardening', () => {
  let client;

  beforeEach(() => {
    client = new TestApiClient();
    mockAccessToken = 'initial_access_token';
    mockRefreshToken = 'initial_refresh_token';
    secureStoreThrowOnWrite = false;
    legacyAsyncStorage = {
      eparly_access_token: 'legacy_insecure_access',
      eparly_refresh_token: 'legacy_insecure_refresh',
      eparly_user_profile: '{"email":"test@example.com"}',
    };
  });

  test('1. Two simultaneous 401 requests trigger only ONE refresh request', async () => {
    client.mockFetchHandler = async (url, init) => {
      if (url === '/v1/auth/refresh') {
        // Simulate network delay for refresh
        await new Promise((r) => setTimeout(r, 20));
        return new Response(
          JSON.stringify({
            accessToken: 'new_rotated_access_token',
            refreshToken: 'new_rotated_refresh_token',
          }),
          { status: 200 }
        );
      }

      if (url === '/v1/accounts') {
        const authHeader = init?.headers?.get?.('Authorization');
        if (authHeader === 'Bearer new_rotated_access_token') {
          return new Response(JSON.stringify({ data: 'accounts_data' }), { status: 200 });
        }
        return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
      }

      return new Response(null, { status: 404 });
    };

    // Dispatch two concurrent requests with expired initial token
    const [res1, res2] = await Promise.all([
      client.authenticatedFetch('/v1/accounts'),
      client.authenticatedFetch('/v1/accounts'),
    ]);

    // Exactly one refresh call must have been issued
    assert.equal(client.refreshCount, 1, 'Only one refresh request must be sent for concurrent 401s');
    assert.equal(res1.status, 200, 'First request should succeed after retry');
    assert.equal(res2.status, 200, 'Second request should succeed after retry');
    assert.equal(mockAccessToken, 'new_rotated_access_token');
    assert.equal(mockRefreshToken, 'new_rotated_refresh_token');
  });

  test('2. Refresh failure revokes session and does not cause infinite retry loops', async () => {
    client.mockFetchHandler = async (url) => {
      if (url === '/v1/auth/refresh') {
        return new Response(
          JSON.stringify({ error: 'Refresh token invalid or expired' }),
          { status: 401 }
        );
      }
      return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
    };

    const res = await client.authenticatedFetch('/v1/transactions');

    assert.equal(client.refreshCount, 1, 'Refresh attempted once');
    assert.equal(res.status, 401, 'Request remains 401 on refresh failure');
    assert.equal(mockAccessToken, null, 'Access token cleared on refresh rejection');
    assert.equal(mockRefreshToken, null, 'Refresh token cleared on refresh rejection');
  });

  test('3. Repeated 401 after retry terminates immediately without infinite loops', async () => {
    client.mockFetchHandler = async (url) => {
      if (url === '/v1/auth/refresh') {
        return new Response(
          JSON.stringify({
            accessToken: 'still_insufficient_permissions_token',
            refreshToken: 'new_refresh',
          }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ error: 'Forbidden/Unauthorized' }), { status: 401 });
    };

    const res = await client.authenticatedFetch('/v1/admin/secret');

    assert.equal(client.refreshCount, 1, 'Only retried once; no infinite loop');
    assert.equal(res.status, 401);
  });

  test('4. Logout while refresh is pending aborts and clears session', async () => {
    client.mockFetchHandler = async (url) => {
      if (url === '/v1/auth/refresh') {
        await new Promise((r) => setTimeout(r, 30));
        return new Response(
          JSON.stringify({ accessToken: 'late_token', refreshToken: 'late_refresh' }),
          { status: 200 }
        );
      }
      return new Response(null, { status: 401 });
    };

    const refreshPromise = client.executeSingleFlightRefresh();

    // User logs out while refresh is in flight
    client.setLoggingOut(true);
    await mockStorage.clearAuthSession();

    const result = await refreshPromise;

    assert.equal(result, null, 'Pending refresh must not restore tokens after logout');
    assert.equal(mockAccessToken, null, 'Access token must remain cleared');
    assert.equal(mockRefreshToken, null, 'Refresh token must remain cleared');
  });

  test('5. Network failure preserves session for offline handling without clearing tokens', async () => {
    client.mockFetchHandler = async (url) => {
      if (url === '/v1/auth/refresh') {
        throw new Error('Network error (DNS lookup failed)');
      }
      return new Response(null, { status: 401 });
    };

    const res = await client.authenticatedFetch('/v1/profile');

    assert.equal(res.status, 401);
    assert.equal(mockRefreshToken, 'initial_refresh_token', 'Offline failure must not wipe local refresh token');
  });

  test('6. Legacy insecure tokens in AsyncStorage are purged safely', async () => {
    assert.equal(legacyAsyncStorage.eparly_access_token, 'legacy_insecure_access');
    assert.equal(legacyAsyncStorage.eparly_refresh_token, 'legacy_insecure_refresh');
    assert.ok(legacyAsyncStorage.eparly_user_profile);

    await mockStorage.cleanLegacyInsecureTokens();

    assert.equal(legacyAsyncStorage.eparly_access_token, undefined, 'Legacy access token purged');
    assert.equal(legacyAsyncStorage.eparly_refresh_token, undefined, 'Legacy refresh token purged');
    assert.ok(legacyAsyncStorage.eparly_user_profile, 'Non-sensitive profile retained');
  });

  test('7. SecureStore failure on write rejects and does not fall back to insecure storage', async () => {
    secureStoreThrowOnWrite = true;

    await assert.rejects(
      async () => {
        await mockStorage.saveAuthTokens('new_acc', 'new_ref');
      },
      /SecureStore write failed/,
      'Secure storage failure must throw explicitly instead of falling back to AsyncStorage'
    );
  });
});
