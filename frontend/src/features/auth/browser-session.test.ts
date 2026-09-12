import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth-store'
import { authenticateBrowserSession, restoreBrowserSession, revokeBrowserSession, signOutBrowserSession, synchronizeBrowserSession } from './browser-session'
import { ApiError } from '../../lib/api-client'

const authApi = vi.hoisted(() => ({ refreshSession: vi.fn(), logout: vi.fn(), revokeSession: vi.fn() }))
vi.mock('./auth-api', () => authApi)

describe('cookie session restoration', () => {
  afterEach(() => vi.restoreAllMocks())
  beforeEach(() => {
    vi.clearAllMocks()
    authApi.logout.mockResolvedValue(undefined)
    localStorage.clear()
    useAuthStore.getState().clearSession()
    useAuthStore.setState({ status: 'restoring' })
    Object.defineProperty(navigator, 'locks', { configurable: true, value: {
      request: vi.fn((_name, callback) => Promise.resolve().then(callback)),
    } })
  })

  it('restores a reload from the cookie without stored tokens', async () => {
    authApi.refreshSession.mockResolvedValue({
      accessToken: 'restored-access', expiresInSeconds: 900,
      user: { id: 'owner', email: 'owner@example.com', displayName: 'Owner' },
    })

    await expect(restoreBrowserSession()).resolves.toBe(true)
    expect(authApi.refreshSession).toHaveBeenCalledWith()
    expect(useAuthStore.getState()).toMatchObject({ accessToken: 'restored-access', status: 'ready' })
    expect(localStorage.getItem('chanter-auth')).toBeNull()
    expect(navigator.locks.request).toHaveBeenCalledWith('chanter-browser-session', expect.any(Function))
  })

  it('finishes anonymous startup when refresh returns no session', async () => {
    authApi.refreshSession.mockResolvedValue(undefined)
    await expect(restoreBrowserSession()).resolves.toBe(false)
    expect(useAuthStore.getState()).toMatchObject({ accessToken: null, status: 'ready' })
  })

  it('shares one refresh and never restores a late response after logout, including after reload', async () => {
    const pending = deferred<ReturnType<typeof session>>()
    authApi.refreshSession.mockReturnValue(pending.promise)
    const first = restoreBrowserSession()
    const second = restoreBrowserSession()
    await vi.waitFor(() => expect(authApi.refreshSession).toHaveBeenCalledTimes(1))
    await signOutBrowserSession()
    pending.resolve(session('owner'))
    await expect(first).resolves.toBe(false)
    await expect(second).resolves.toBe(false)
    useAuthStore.setState({ status: 'restoring' })
    await expect(restoreBrowserSession()).resolves.toBe(false)
    expect(authApi.refreshSession).toHaveBeenCalledTimes(1)
    expect(authApi.logout).toHaveBeenCalledWith()
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('does not clear a newly signed-in account when an older refresh fails', async () => {
    useAuthStore.getState().setSession(session('owner'))
    const pending = deferred<ReturnType<typeof session>>()
    authApi.refreshSession.mockReturnValue(pending.promise)
    const attempt = restoreBrowserSession()
    await vi.waitFor(() => expect(authApi.refreshSession).toHaveBeenCalled())
    await authenticateBrowserSession(async () => session('learner'))
    pending.reject(new ApiError('Expired', 401))
    await expect(attempt).resolves.toBe(false)
    expect(useAuthStore.getState().user?.id).toBe('learner')
  })

  it('clears this tab when another tab changes the browser account', () => {
    useAuthStore.getState().setSession(session('owner'))
    const stop = synchronizeBrowserSession()
    window.dispatchEvent(new StorageEvent('storage', { key: 'chanter-session-change', newValue: 'signed-out:other-tab' }))
    expect(useAuthStore.getState().user).toBeNull()
    stop()
  })

  it('keeps a failed logout signed out on subsequent startup', async () => {
    authApi.logout.mockRejectedValue(new TypeError('Network unavailable'))
    await expect(signOutBrowserSession()).rejects.toThrow('Network unavailable')
    await expect(restoreBrowserSession()).resolves.toBe(false)
    expect(authApi.refreshSession).not.toHaveBeenCalled()
  })

  it('fails closed without cross-tab locking support', async () => {
    Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
    await expect(restoreBrowserSession()).resolves.toBe(false)
    expect(authApi.refreshSession).not.toHaveBeenCalled()
    expect(useAuthStore.getState().status).toBe('unavailable')
  })

  it('explains blocked site storage and never starts an uncoordinated sign-in', async () => {
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError') })
    const operation = vi.fn()
    await expect(authenticateBrowserSession(operation)).rejects.toThrow('Allow site storage')
    expect(operation).not.toHaveBeenCalled()
  })

  it('waits for refresh to finish before sending cookie-backed logout', async () => {
    let queue = Promise.resolve<unknown>(undefined)
    Object.defineProperty(navigator, 'locks', { configurable: true, value: {
      request: vi.fn((_name, callback) => {
        const request = queue.then(callback)
        queue = request.catch(() => undefined)
        return request
      }),
    } })
    const pending = deferred<ReturnType<typeof session>>()
    authApi.refreshSession.mockReturnValue(pending.promise)
    const refresh = restoreBrowserSession()
    await vi.waitFor(() => expect(authApi.refreshSession).toHaveBeenCalled())
    const logout = signOutBrowserSession()
    expect(authApi.logout).not.toHaveBeenCalled()
    pending.resolve(session('owner'))
    await logout
    await expect(refresh).resolves.toBe(false)
    expect(authApi.logout).toHaveBeenCalledTimes(1)
  })

  it('refreshes an expired access token outside the remote revocation lock and retries once', async () => {
    useAuthStore.getState().setSession(session('owner'))
    authApi.revokeSession.mockRejectedValueOnce(new ApiError('Expired', 401)).mockResolvedValueOnce(undefined)
    authApi.refreshSession.mockResolvedValue(session('owner'))
    await revokeBrowserSession('other-device')
    expect(authApi.revokeSession).toHaveBeenCalledTimes(2)
    expect(authApi.refreshSession).toHaveBeenCalledTimes(1)
  })
})

function session(id: string) {
  return { accessToken: `access-${id}`, expiresInSeconds: 900,
    user: { id, email: `${id}@example.com`, displayName: id } }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
