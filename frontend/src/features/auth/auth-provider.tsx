"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { apiFetch, ApiClientError } from "@/lib/api-client";
import type { AuthResponse, Me } from "@/types/api";
import { authApi, refreshSession } from "./api";

type AuthState =
  | { status: "loading"; user: null }
  | { status: "authenticated"; user: Me }
  | { status: "anonymous"; user: null };

interface AuthContextValue {
  state: AuthState;
  /** Store a session returned by login, register or invitation acceptance. */
  startSession: (response: AuthResponse) => void;
  /** Update the cached profile after it changes (e.g. name edit). */
  setUser: (user: Me) => void;
  logout: () => Promise<void>;
  /** Authenticated API call. Retries once after refreshing an expired access token. */
  authFetch: <T>(path: string, init?: RequestInit) => Promise<T>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Refresh this long before the access token expires. */
const REFRESH_MARGIN_MS = 60_000;

/**
 * Holds the session. The access token lives only in memory (never localStorage), so it is not
 * exposed to persistent XSS theft; the long-lived refresh token is an httpOnly cookie the page
 * cannot read. On load, the session is restored by calling /auth/refresh.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading", user: null });
  const accessToken = useRef<string | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearSession = useCallback(() => {
    accessToken.current = null;
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
    setState({ status: "anonymous", user: null });
  }, []);

  const applySession = useCallback(
    function apply(response: AuthResponse) {
      accessToken.current = response.accessToken;
      setState({ status: "authenticated", user: response.user });
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
      const delay = Math.max(response.expiresIn * 1000 - REFRESH_MARGIN_MS, 10_000);
      refreshTimer.current = setTimeout(() => {
        refreshSession().then(apply, clearSession);
      }, delay);
    },
    [clearSession],
  );

  useEffect(() => {
    refreshSession().then(applySession, clearSession);
    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
    };
  }, [applySession, clearSession]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const authFetch = useCallback(
    async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
      const call = (token: string | null) => {
        const headers = new Headers(init.headers);
        if (token) headers.set("Authorization", `Bearer ${token}`);
        return apiFetch<T>(path, { ...init, headers });
      };
      try {
        return await call(accessToken.current);
      } catch (err) {
        if (!(err instanceof ApiClientError) || err.status !== 401) throw err;
        let refreshed: AuthResponse;
        try {
          refreshed = await refreshSession();
        } catch {
          clearSession();
          throw err;
        }
        applySession(refreshed);
        return call(refreshed.accessToken);
      }
    },
    [applySession, clearSession],
  );

  const setUser = useCallback((user: Me) => setState({ status: "authenticated", user }), []);

  const value = useMemo(
    () => ({ state, startSession: applySession, setUser, logout, authFetch }),
    [state, applySession, setUser, logout, authFetch],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
  return context;
}

/** For pages inside the authenticated app shell, where the user is guaranteed to exist. */
export function useCurrentUser(): Me {
  const { state } = useAuth();
  if (state.status !== "authenticated") throw new Error("useCurrentUser requires an authenticated session");
  return state.user;
}
