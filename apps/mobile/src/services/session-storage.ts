import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const ACCESS_TOKEN_KEY = 'eparly_access_token';
const REFRESH_TOKEN_KEY = 'eparly_refresh_token';
const USER_PROFILE_KEY = 'eparly_user_profile';

export interface StoredUserProfile {
  id: string;
  email: string;
  status: string;
  emailVerifiedAt?: string | null;
  createdAt?: string;
}

async function setSecureItem(key: string, value: string): Promise<void> {
  if (Platform.OS === 'web') {
    await AsyncStorage.setItem(key, value);
    return;
  }
  try {
    await SecureStore.setItemAsync(key, value);
  } catch {
    await AsyncStorage.setItem(key, value);
  }
}

async function getSecureItem(key: string): Promise<string | null> {
  if (Platform.OS === 'web') {
    return AsyncStorage.getItem(key);
  }
  try {
    return await SecureStore.getItemAsync(key);
  } catch {
    return AsyncStorage.getItem(key);
  }
}

async function removeSecureItem(key: string): Promise<void> {
  if (Platform.OS === 'web') {
    await AsyncStorage.removeItem(key);
    return;
  }
  try {
    await SecureStore.deleteItemAsync(key);
  } catch {
    await AsyncStorage.removeItem(key);
  }
}

/**
 * Persists the session tokens securely.
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
 * Persists the user profile in storage.
 */
export async function saveUserProfile(user: StoredUserProfile): Promise<void> {
  await AsyncStorage.setItem(USER_PROFILE_KEY, JSON.stringify(user));
}

/**
 * Retrieves the cached user profile.
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
 * Clears all auth session data (tokens & cached profile).
 */
export async function clearAuthSession(): Promise<void> {
  await Promise.all([
    removeSecureItem(ACCESS_TOKEN_KEY),
    removeSecureItem(REFRESH_TOKEN_KEY),
    AsyncStorage.removeItem(USER_PROFILE_KEY),
  ]);
}
