import { create } from 'zustand'
import { persist } from 'zustand/middleware'

import type { AuthSession, AuthUser } from '../features/auth/types'

type AuthStore = {
  accessToken: string | null
  refreshToken: string | null
  user: AuthUser | null
  setSession: (session: AuthSession) => void
  clearSession: () => void
}

type PersistedAuthState = {
  accessToken: string | null
  user: AuthUser | null
}

/**
 * Persist only short-lived access + user profile for reload within access TTL.
 * Refresh stays in memory so XSS cannot read a renewable token from storage (SEC-06).
 */
export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (session) =>
        set({
          accessToken: session.accessToken,
          refreshToken: session.refreshToken,
          user: session.user,
        }),
      clearSession: () =>
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
        }),
    }),
    {
      name: 'chanter-auth',
      version: 1,
      partialize: (state): PersistedAuthState => ({
        accessToken: state.accessToken,
        user: state.user,
      }),
      migrate: (persistedState): PersistedAuthState => {
        if (!persistedState || typeof persistedState !== 'object') {
          return { accessToken: null, user: null }
        }
        const legacy = persistedState as Partial<PersistedAuthState> & {
          refreshToken?: string | null
        }
        return {
          accessToken: legacy.accessToken ?? null,
          user: legacy.user ?? null,
        }
      },
    },
  ),
)

export function isAuthenticated(): boolean {
  return useAuthStore.getState().accessToken !== null
}
