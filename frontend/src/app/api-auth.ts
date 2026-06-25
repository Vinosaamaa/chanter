import { configureApiAuth } from '../lib/api-client'
import { refreshSession } from '../features/auth/auth-api'
import { useAuthStore } from '../stores/auth-store'

let refreshInFlight: Promise<boolean> | null = null

configureApiAuth({
  getAccessToken: () => useAuthStore.getState().accessToken,
  refreshSession: async () => {
    if (refreshInFlight) {
      return refreshInFlight
    }

    refreshInFlight = (async () => {
      const currentRefreshToken = useAuthStore.getState().refreshToken
      if (!currentRefreshToken) {
        return false
      }

      try {
        const session = await refreshSession(currentRefreshToken)
        useAuthStore.getState().setSession(session)
        return true
      } catch {
        useAuthStore.getState().clearSession()
        return false
      }
    })().finally(() => {
      refreshInFlight = null
    })

    return refreshInFlight
  },
})
