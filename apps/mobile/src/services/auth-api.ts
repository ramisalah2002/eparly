import Constants from 'expo-constants';
import { Platform } from 'react-native';

import {
  clearAuthSession,
  getRefreshToken,
  saveAuthTokens,
  saveUserProfile,
  StoredUserProfile,
} from './session-storage';

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterData {
  email: string;
  password: string;
  termsAccepted: boolean;
  marketingConsent?: boolean;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
}

export interface MessageResponse {
  message: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  statusCode?: number;
}

export function getApiBaseUrl(): string {
  if (process.env.EXPO_PUBLIC_API_URL) {
    return process.env.EXPO_PUBLIC_API_URL;
  }

  // Resolve local development host dynamically for Expo Go on physical devices
  const debuggerHost = Constants.expoConfig?.hostUri;
  if (debuggerHost) {
    const ip = debuggerHost.split(':')[0];
    if (ip && ip !== 'localhost') {
      return `http://${ip}:8080`;
    }
  }

  if (Platform.OS === 'android') {
    return 'http://10.0.2.2:8080';
  }
  return 'http://localhost:8080';
}

function parseErrorMessage(body: unknown, fallback: string): string {
  if (typeof body === 'object' && body !== null) {
    const record = body as Record<string, unknown>;
    if (typeof record.message === 'string') return record.message;
    if (typeof record.error === 'string') return record.error;
  }
  return fallback;
}

/**
 * Signs in an existing user with email and password.
 */
export async function loginToApi(credentials: LoginCredentials): Promise<ApiResponse<TokenResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/login`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 9000);

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        email: credentials.email.trim().toLowerCase(),
        password: credentials.password,
      }),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (response.ok) {
      const data = (await response.json()) as TokenResponse;
      await saveAuthTokens(data.accessToken, data.refreshToken);
      return { success: true, data };
    }

    let error = 'Invalid email or password.';
    if (response.status === 401) {
      error = 'Invalid email or password. Please verify your credentials or check if your email has been verified.';
    } else if (response.status === 429) {
      error = 'Too many failed login attempts. Please wait a moment before trying again.';
    } else {
      try {
        const body = await response.json();
        error = parseErrorMessage(body, error);
      } catch {
        // use fallback
      }
    }

    return { success: false, error, statusCode: response.status };
  } catch (err: unknown) {
    clearTimeout(timeoutId);
    const isTimeout = err instanceof Error && err.name === 'AbortError';
    return {
      success: false,
      error: isTimeout
        ? 'Connection timed out. Please verify your backend server.'
        : 'Unable to reach the server. Please check your network connection.',
    };
  }
}

/**
 * Registers a new user account.
 */
export async function registerToApi(data: RegisterData): Promise<ApiResponse<MessageResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/register`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 9000);

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        email: data.email.trim().toLowerCase(),
        password: data.password,
        termsAccepted: data.termsAccepted,
        marketingConsent: Boolean(data.marketingConsent),
      }),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (response.status === 201 || response.ok) {
      const resData = (await response.json()) as MessageResponse;
      return { success: true, data: resData };
    }

    let error = 'Could not create account.';
    if (response.status === 409) {
      error = 'An account with this email already exists.';
    } else if (response.status === 400) {
      try {
        const body = await response.json();
        error = parseErrorMessage(body, 'Invalid registration information.');
      } catch {
        error = 'Invalid registration information.';
      }
    } else if (response.status === 429) {
      error = 'Too many attempts. Please try again later.';
    } else if (response.status === 503) {
      error = 'Email service is currently unavailable. Please try again later.';
    }

    return { success: false, error, statusCode: response.status };
  } catch (err: unknown) {
    clearTimeout(timeoutId);
    const isTimeout = err instanceof Error && err.name === 'AbortError';
    return {
      success: false,
      error: isTimeout
        ? 'Registration timed out. Please try again.'
        : 'Unable to reach the server. Please verify your connection.',
    };
  }
}

/**
 * Verifies email with token sent to user's inbox.
 */
export async function verifyEmailApi(token: string): Promise<ApiResponse<MessageResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/verify-email`;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ token: token.trim() }),
    });

    if (response.ok) {
      const data = (await response.json()) as MessageResponse;
      return { success: true, data };
    }

    let error = 'Verification token is invalid or expired.';
    try {
      const body = await response.json();
      error = parseErrorMessage(body, error);
    } catch {
      // use fallback
    }

    return { success: false, error, statusCode: response.status };
  } catch {
    return {
      success: false,
      error: 'Unable to reach the verification server.',
    };
  }
}

/**
 * Resends email verification code to user email.
 */
export async function resendVerificationApi(email: string): Promise<ApiResponse<MessageResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/resend-verification`;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ email: email.trim().toLowerCase() }),
    });

    if (response.ok) {
      const data = (await response.json()) as MessageResponse;
      return { success: true, data };
    }

    let error = 'Unable to resend verification.';
    try {
      const body = await response.json();
      error = parseErrorMessage(body, error);
    } catch {
      // use fallback
    }

    return { success: false, error, statusCode: response.status };
  } catch {
    return { success: false, error: 'Network error while requesting verification email.' };
  }
}

/**
 * Sends a 6-digit password reset code to the user's email address.
 */
export async function forgotPasswordApi(email: string): Promise<ApiResponse<MessageResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/forgot-password`;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ email: email.trim().toLowerCase() }),
    });

    if (response.ok) {
      const data = (await response.json()) as MessageResponse;
      return { success: true, data };
    }

    let error = 'Unable to send password reset code.';
    try {
      const body = await response.json();
      error = parseErrorMessage(body, error);
    } catch {
      // fallback
    }

    return { success: false, error, statusCode: response.status };
  } catch {
    return { success: false, error: 'Network error while requesting password reset code.' };
  }
}

/**
 * Verifies that the 6-digit reset code is valid and active before prompting for a new password.
 */
export async function verifyResetCodeApi(code: string): Promise<ApiResponse<MessageResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/verify-reset-code`;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ code: code.trim() }),
    });

    if (response.ok) {
      const data = (await response.json()) as MessageResponse;
      return { success: true, data };
    }

    let error = 'Invalid or expired reset code.';
    try {
      const body = await response.json();
      error = parseErrorMessage(body, error);
    } catch {
      // fallback
    }

    return { success: false, error, statusCode: response.status };
  } catch {
    return { success: false, error: 'Network error while verifying reset code.' };
  }
}

/**
 * Resets the user's password using the verified code and new password.
 */
export async function resetPasswordApi(token: string, newPassword: string): Promise<ApiResponse<MessageResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/reset-password`;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ token: token.trim(), newPassword }),
    });

    if (response.ok) {
      const data = (await response.json()) as MessageResponse;
      return { success: true, data };
    }

    let error = 'Unable to reset password.';
    try {
      const body = await response.json();
      error = parseErrorMessage(body, error);
    } catch {
      // fallback
    }

    return { success: false, error, statusCode: response.status };
  } catch {
    return { success: false, error: 'Network error while resetting password.' };
  }
}

/**
 * Rotates the refresh token and acquires a new access token.
 */
export async function refreshTokensApi(refreshToken: string): Promise<ApiResponse<TokenResponse>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/refresh`;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    });

    if (response.ok) {
      const data = (await response.json()) as TokenResponse;
      await saveAuthTokens(data.accessToken, data.refreshToken);
      return { success: true, data };
    }

    return {
      success: false,
      error: 'Session expired. Please sign in again.',
      statusCode: response.status,
    };
  } catch {
    return { success: false, error: 'Network error during session renewal.' };
  }
}

/**
 * Fetches the authenticated user profile (/v1/me).
 */
export async function fetchUserProfileApi(accessToken: string): Promise<ApiResponse<StoredUserProfile>> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/me`;

  try {
    const response = await fetch(endpoint, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: 'application/json',
      },
    });

    if (response.ok) {
      const data = (await response.json()) as StoredUserProfile;
      await saveUserProfile(data);
      return { success: true, data };
    }

    return {
      success: false,
      error: 'Could not fetch user profile.',
      statusCode: response.status,
    };
  } catch {
    return { success: false, error: 'Network error while fetching profile.' };
  }
}

/**
 * Logs out and revokes the active session on the backend.
 */
export async function logoutApi(): Promise<void> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/logout`;

  try {
    const token = await getRefreshToken();
    if (token) {
      await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken: token }),
      }).catch(() => {});
    }
  } finally {
    await clearAuthSession();
  }
}
