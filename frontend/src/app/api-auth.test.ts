import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthSession } from '../features/auth/types'
import { useAuthStore } from '../stores/auth-store'

type ApiAuthConfiguration = {
  refreshSession: () => Promise<boolean>
}

const apiClient = vi.hoisted(() => ({ configureApiAuth: vi.fn() }))
const authApi = vi.hoisted(() => ({ refreshSession: vi.fn() }))

vi.mock('../lib/api-client', () => ({ configureApiAuth: apiClient.configureApiAuth }))
vi.mock('../features/auth/auth-api', () => ({ refreshSession: authApi.refreshSession }))

describe('API authentication account boundary', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    useAuthStore.getState().clearSession()
  })

  afterEach(() => useAuthStore.getState().clearSession())

  it('does not restore a session from a refresh completed after sign-out', async () => {
    let configured: ApiAuthConfiguration | undefined
    apiClient.configureApiAuth.mockImplementation((configuration: ApiAuthConfiguration) => {
      configured = configuration
    })
    useAuthStore.getState().setSession(sessionFor('owner', 'refresh-owner'))

    let resolveRefresh: ((session: AuthSession) => void) | undefined
    authApi.refreshSession.mockReturnValue(new Promise<AuthSession>((resolve) => {
      resolveRefresh = resolve
    }))
    await import('./api-auth')

    const refreshAttempt = configured?.refreshSession()
    expect(refreshAttempt).toBeDefined()
    useAuthStore.getState().clearSession()
    resolveRefresh?.(sessionFor('owner', 'refresh-owner-next'))

    await expect(refreshAttempt).resolves.toBe(false)
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
  })
})

function sessionFor(userId: string, refreshToken: string): AuthSession {
  return {
    accessToken: `access-${userId}`,
    refreshToken,
    expiresInSeconds: 900,
    user: {
      id: userId,
      email: `${userId}@chanter.local`,
      displayName: userId,
    },
  }
}
