import { useAuthStore } from '../../../stores/auth-store'
import { createStudyServerCta } from '../marketing-routes'

export function useStudyServerCta() {
  const isAuthenticated = Boolean(useAuthStore((state) => state.accessToken))
  return createStudyServerCta(isAuthenticated)
}
