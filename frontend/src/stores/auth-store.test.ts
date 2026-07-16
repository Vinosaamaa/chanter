import { beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from './auth-store'

const STORAGE_KEY = 'chanter-auth'

const sampleUser = {
  id: 'user-1',
  email: 'owner@chanter.local',
  displayName: 'Owner',
}

function readPersisted(): Record<string, unknown> | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }
  return JSON.parse(raw) as Record<string, unknown>
}

describe('auth-store SEC-06 refresh token storage', () => {
  beforeEach(async () => {
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      user: null,
    })
    await useAuthStore.persist.rehydrate()
  })

  it('keeps refreshToken in memory but does not persist it to localStorage', () => {
    useAuthStore.getState().setSession({
      accessToken: 'access-token',
      refreshToken: 'refresh-token-secret',
      expiresInSeconds: 900,
      user: sampleUser,
    })

    const memory = useAuthStore.getState()
    expect(memory.refreshToken).toBe('refresh-token-secret')
    expect(memory.accessToken).toBe('access-token')

    const persisted = readPersisted()
    expect(persisted).not.toBeNull()
    const state = persisted!.state as Record<string, unknown>
    expect(state.accessToken).toBe('access-token')
    expect(state.user).toEqual(sampleUser)
    expect(state).not.toHaveProperty('refreshToken')
    expect(JSON.stringify(persisted)).not.toContain('refresh-token-secret')
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('clearSession removes tokens from memory and storage', () => {
    useAuthStore.getState().setSession({
      accessToken: 'access-token',
      refreshToken: 'refresh-token-secret',
      expiresInSeconds: 900,
      user: sampleUser,
    })

    useAuthStore.getState().clearSession()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: null,
      refreshToken: null,
      user: null,
    })
    const persisted = readPersisted()
    const state = persisted?.state as Record<string, unknown> | undefined
    expect(state?.accessToken ?? null).toBeNull()
    expect(state?.user ?? null).toBeNull()
    expect(JSON.stringify(persisted ?? {})).not.toContain('refresh-token-secret')
  })

  it('migrate strips legacy persisted refreshToken on rehydrate', async () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        state: {
          accessToken: 'legacy-access',
          refreshToken: 'legacy-refresh-should-not-survive',
          user: sampleUser,
        },
        version: 0,
      }),
    )

    await useAuthStore.persist.rehydrate()

    expect(useAuthStore.getState().accessToken).toBe('legacy-access')
    expect(useAuthStore.getState().user).toEqual(sampleUser)
    // Refresh must not be restored from storage into a durable persisted blob.
    expect(JSON.stringify(readPersisted())).not.toContain('legacy-refresh-should-not-survive')
  })
})
