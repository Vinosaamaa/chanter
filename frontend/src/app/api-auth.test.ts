import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch, getApiAccessToken } from '../lib/api-client'
import { useAuthStore } from '../stores/auth-store'
import './api-auth'

const authApi = vi.hoisted(() => ({ refreshSession: vi.fn() }))
vi.mock('../features/auth/auth-api', () => authApi)

describe('configured browser API authentication', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    Object.defineProperty(navigator, 'locks', { configurable: true, value: {
      request: vi.fn((_name, callback) => Promise.resolve().then(callback)),
    } })
    useAuthStore.getState().clearSession()
  })

  it('retries using a cookie-refreshed memory token for the same account', async () => {
    const user = { id: 'owner', email: 'owner@example.com', displayName: 'Owner' }
    useAuthStore.getState().setSession({ accessToken: 'expired', expiresInSeconds: 900, user })
    authApi.refreshSession.mockResolvedValue({ accessToken: 'renewed', expiresInSeconds: 900, user })
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ title: 'Course' }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiFetch('/api/v1/courses')).resolves.toEqual({ title: 'Course' })
    expect(authApi.refreshSession).toHaveBeenCalledWith()
    expect(getApiAccessToken()).toBe('renewed')
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe('Bearer renewed')
  })
})
