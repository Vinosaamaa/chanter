import { configureApiAuth } from '../lib/api-client'
import { currentBrowserSessionChange, restoreBrowserSession } from '../features/auth/browser-session'
import { useAuthStore } from '../stores/auth-store'

configureApiAuth({
  getAccessToken: () => useAuthStore.getState().accessToken,
  getSessionGeneration: () => `${useAuthStore.getState().generation}:${currentBrowserSessionChange()}`,
  refreshSession: restoreBrowserSession,
})
