import { Platform } from 'react-native';

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
}

export interface LoginSuccessResult {
  success: true;
  data: TokenResponse;
}

export interface LoginErrorResult {
  success: false;
  error: string;
  statusCode?: number;
}

export type LoginResult = LoginSuccessResult | LoginErrorResult;

function getApiBaseUrl(): string {
  if (process.env.EXPO_PUBLIC_API_URL) {
    return process.env.EXPO_PUBLIC_API_URL;
  }
  if (Platform.OS === 'android') {
    return 'http://10.0.2.2:8080';
  }
  return 'http://localhost:8080';
}

export async function loginToApi(credentials: LoginCredentials): Promise<LoginResult> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/v1/auth/login`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        email: credentials.email.trim(),
        password: credentials.password,
      }),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (response.ok) {
      const data = (await response.json()) as TokenResponse;
      return { success: true, data };
    }

    if (response.status === 401) {
      return {
        success: false,
        error: 'Invalid credentials. Please check your username and password.',
        statusCode: 401,
      };
    }

    if (response.status === 429) {
      return {
        success: false,
        error: 'Too many attempts. Please try again in a few moments.',
        statusCode: 429,
      };
    }

    let errorMessage = 'An error occurred. Please try again.';
    try {
      const errBody = await response.json();
      if (errBody && typeof errBody.message === 'string') {
        errorMessage = errBody.message;
      }
    } catch {
      // Ignore parse error
    }

    return {
      success: false,
      error: errorMessage,
      statusCode: response.status,
    };
  } catch (error: unknown) {
    clearTimeout(timeoutId);

    if (error instanceof Error && error.name === 'AbortError') {
      return {
        success: false,
        error: 'Connection timed out. Please try again.',
      };
    }

    return {
      success: false,
      error: 'Unable to reach the server. Please check your network connection.',
    };
  }
}
