import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import {
  fetchUserProfileApi,
  LoginCredentials,
  loginToApi,
  logoutApi,
  refreshTokensApi,
  RegisterData,
  registerToApi,
  verifyEmailApi,
} from '@/services/auth-api';
import {
  clearAuthSession,
  getAccessToken,
  getRefreshToken,
  getUserProfile,
  StoredUserProfile,
} from '@/services/session-storage';

interface AuthContextValue {
  user: StoredUserProfile | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  signIn: (credentials: LoginCredentials) => Promise<{ success: boolean; error?: string }>;
  signUp: (data: RegisterData) => Promise<{ success: boolean; error?: string; message?: string }>;
  verifyEmail: (token: string) => Promise<{ success: boolean; error?: string }>;
  signOut: () => Promise<void>;
  checkSession: () => Promise<boolean>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<StoredUserProfile | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const checkSession = useCallback(async (): Promise<boolean> => {
    try {
      const storedAccessToken = await getAccessToken();
      const storedRefreshToken = await getRefreshToken();

      if (!storedAccessToken && !storedRefreshToken) {
        setUser(null);
        setToken(null);
        return false;
      }

      // Try with current access token
      if (storedAccessToken) {
        const profileRes = await fetchUserProfileApi(storedAccessToken);
        if (profileRes.success && profileRes.data) {
          setUser(profileRes.data);
          setToken(storedAccessToken);
          return true;
        }
      }

      // If access token was expired, try rotating with refresh token
      if (storedRefreshToken) {
        const refreshRes = await refreshTokensApi(storedRefreshToken);
        if (refreshRes.success && refreshRes.data) {
          setToken(refreshRes.data.accessToken);
          const profileRes = await fetchUserProfileApi(refreshRes.data.accessToken);
          if (profileRes.success && profileRes.data) {
            setUser(profileRes.data);
          }
          return true;
        }
      }

      // Neither token worked
      await clearAuthSession();
      setUser(null);
      setToken(null);
      return false;
    } catch {
      // Fallback to cached profile if offline
      const cached = await getUserProfile();
      if (cached) {
        setUser(cached);
        return true;
      }
      return false;
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    // eslint-disable-next-line react-hooks/set-state-in-effect
    checkSession().finally(() => {
      if (isMounted) {
        setIsLoading(false);
      }
    });

    return () => {
      isMounted = false;
    };
  }, [checkSession]);

  const signIn = useCallback(
    async (credentials: LoginCredentials) => {
      const res = await loginToApi(credentials);
      if (!res.success || !res.data) {
        return { success: false, error: res.error || 'Login failed' };
      }

      setToken(res.data.accessToken);
      const profileRes = await fetchUserProfileApi(res.data.accessToken);
      if (profileRes.success && profileRes.data) {
        setUser(profileRes.data);
      } else {
        setUser({
          id: 'user',
          email: credentials.email.trim().toLowerCase(),
          status: 'ACTIVE',
        });
      }

      return { success: true };
    },
    []
  );

  const signUp = useCallback(async (data: RegisterData) => {
    const res = await registerToApi(data);
    if (!res.success) {
      return { success: false, error: res.error || 'Registration failed' };
    }
    return { success: true, message: res.data?.message };
  }, []);

  const verifyEmail = useCallback(async (verifyToken: string) => {
    const res = await verifyEmailApi(verifyToken);
    if (!res.success) {
      return { success: false, error: res.error || 'Verification failed' };
    }
    return { success: true };
  }, []);

  const signOut = useCallback(async () => {
    await logoutApi();
    setUser(null);
    setToken(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isLoading,
      isAuthenticated: Boolean(token && user),
      signIn,
      signUp,
      verifyEmail,
      signOut,
      checkSession,
    }),
    [user, token, isLoading, signIn, signUp, verifyEmail, signOut, checkSession]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
