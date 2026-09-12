import { create } from 'zustand'

import type { AuthSession, AuthUser } from '../features/auth/types'

type AuthStore = {
  accessToken: string | null
  user: AuthUser | null
  status: 'restoring' | 'ready' | 'unavailable'
  generation: number
  setSession: (session: AuthSession) => void
  clearSession: () => void
}

// Remove pre-cookie credentials without ever hydrating them into the new session.
try {
  localStorage.removeItem('chanter-auth')
  sessionStorage.removeItem('chanter-auth')
} catch {
  // Storage may be disabled. Authentication itself remains in memory.
}

export const useAuthStore = create<AuthStore>((set) => ({
  accessToken: null,
  user: null,
  status: 'restoring',
  generation: 0,
  setSession: (session) => set((state) => ({
    accessToken: session.accessToken,
    user: session.user,
    status: 'ready',
    generation: state.generation + (state.user?.id === session.user.id ? 0 : 1),
  })),
  clearSession: () => set((state) => ({
    accessToken: null,
    user: null,
    status: 'ready',
    generation: state.generation + 1,
  })),
}))

export function isAuthenticated(): boolean {
  return useAuthStore.getState().accessToken !== null
}
