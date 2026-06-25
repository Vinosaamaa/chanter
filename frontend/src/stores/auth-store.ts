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
    },
  ),
)

export function isAuthenticated(): boolean {
  return useAuthStore.getState().accessToken !== null
}
