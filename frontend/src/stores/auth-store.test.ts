import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from './auth-store'

describe('browser credential storage', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.getState().clearSession()
  })

  it('keeps the access token only in memory and never stores a renewable credential', () => {
    useAuthStore.getState().setSession({
      accessToken: 'access-secret',
      expiresInSeconds: 900,
      user: { id: 'owner', email: 'owner@example.com', displayName: 'Owner' },
    })
    expect(useAuthStore.getState().accessToken).toBe('access-secret')
    expect(useAuthStore.getState()).not.toHaveProperty('refreshToken')
    expect(localStorage.getItem('chanter-auth')).toBeNull()
    expect(sessionStorage.getItem('chanter-auth')).toBeNull()
  })

  it('discards credentials from older releases before restoring a cookie session', async () => {
    localStorage.setItem('chanter-auth', JSON.stringify({ state: {
      accessToken: 'old-access', refreshToken: 'old-refresh', user: { id: 'owner' },
    }, version: 1 }))
    vi.resetModules()
    const { useAuthStore: freshStore } = await import('./auth-store')
    expect(freshStore.getState().accessToken).toBeNull()
    expect(freshStore.getState().user).toBeNull()
    expect(localStorage.getItem('chanter-auth')).toBeNull()
  })
})
