import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const ACCESS_TOKEN_KEY = 'eparly_access_token';
const REFRESH_TOKEN_KEY = 'eparly_refresh_token';
const USER_PROFILE_KEY = 'eparly_user_profile';

export interface StoredUserProfile {
  id: string;
  email: string;
  fullName?: string | null;
  status: string;
  emailVerifiedAt?: string | null;
  createdAt?: string;
}

/**
 * Stores a secret key-value pair strictly in SecureStore on native devices.
 * Throws an explicit error if SecureStore fails on native to prevent insecure fallback.
 */
async function setSecureItem(key: string, value: string): Promise<void> {
  if (Platform.OS === 'web') {
    await AsyncStorage.setItem(key, value);
    return;
  }

  // On native platforms (iOS, Android), write strictly to SecureStore.
  // Never fall back to AsyncStorage for cryptographic or session secrets.
  await SecureStore.setItemAsync(key, value, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

/**
 * Retrieves a secret key-value pair strictly from SecureStore on native devices.
 */
async function getSecureItem(key: string): Promise<string | null> {
  if (Platform.OS === 'web') {
    return AsyncStorage.getItem(key);
  }

  return SecureStore.getItemAsync(key, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

/**
 * Removes a secret key-value pair from SecureStore and cleans up any legacy storage.
 */
async function removeSecureItem(key: string): Promise<void> {
  if (Platform.OS === 'web') {
    await AsyncStorage.removeItem(key);
    return;
  }

  try {
    await SecureStore.deleteItemAsync(key, {
      keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
    });
  } catch {
    // Continue to legacy cleanup
  }

  // Always scrub any legacy insecure key that might have been stored in older app versions
  try {
    await AsyncStorage.removeItem(key);
  } catch {
    // Ignore legacy cleanup errors
  }
}

/**
 * Purges legacy insecure token keys from AsyncStorage without migrating them.
 * This ensures credentials that may have been written by older versions are safely eradicated.
 */
export async function cleanLegacyInsecureTokens(): Promise<void> {
  try {
    await AsyncStorage.multiRemove([ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY]);
  } catch {
    // Non-fatal if storage is unavailable
  }
}

/**
 * Persists the session tokens securely.
 * Rejects if SecureStore fails so callers never assume an unauthenticated write succeeded.
 */
export async function saveAuthTokens(accessToken: string, refreshToken: string): Promise<void> {
  await Promise.all([
    setSecureItem(ACCESS_TOKEN_KEY, accessToken),
    setSecureItem(REFRESH_TOKEN_KEY, refreshToken),
  ]);
}

/**
 * Retrieves the stored access token.
 */
export async function getAccessToken(): Promise<string | null> {
  return getSecureItem(ACCESS_TOKEN_KEY);
}

/**
 * Retrieves the stored refresh token.
 */
export async function getRefreshToken(): Promise<string | null> {
  return getSecureItem(REFRESH_TOKEN_KEY);
}

/**
 * Persists the non-sensitive user profile in standard storage.
 */
export async function saveUserProfile(user: StoredUserProfile): Promise<void> {
  await AsyncStorage.setItem(USER_PROFILE_KEY, JSON.stringify(user));
}

/**
 * Retrieves the cached non-sensitive user profile.
 */
export async function getUserProfile(): Promise<StoredUserProfile | null> {
  try {
    const raw = await AsyncStorage.getItem(USER_PROFILE_KEY);
    return raw ? (JSON.parse(raw) as StoredUserProfile) : null;
  } catch {
    return null;
  }
}

/**
 * Clears all auth session data (secure tokens, legacy tokens, and cached profile).
 */
export async function clearAuthSession(): Promise<void> {
  await Promise.all([
    removeSecureItem(ACCESS_TOKEN_KEY),
    removeSecureItem(REFRESH_TOKEN_KEY),
    AsyncStorage.removeItem(USER_PROFILE_KEY),
  ]);
}
