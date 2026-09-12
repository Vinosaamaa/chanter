import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { logout, refreshSession, resetPassword, revokeSession, type RegisterResponse } from './auth-api'

// This marker contains no credentials or account data. It prevents a failed logout
// from silently restoring the still-present HttpOnly cookie after a reload.
const CHANGE_KEY = 'chanter-session-change'
let refreshInFlight: Promise<boolean> | null = null

export function currentBrowserSessionChange(): string | null {
  try {
    return localStorage.getItem(CHANGE_KEY)
  } catch {
    return 'signed-out:storage-unavailable'
  }
}

function isSignedOut(): boolean {
  return currentBrowserSessionChange()?.startsWith('signed-out:') ?? false
}

function publishChange(action: 'active' | 'signed-out'): string {
  const change = `${action}:${crypto.randomUUID()}`
  try {
    localStorage.setItem(CHANGE_KEY, change)
  } catch (cause) {
    throw new Error('Allow site storage in your browser to securely manage your Chanter session, then try again.', { cause })
  }
  return change
}

export function synchronizeBrowserSession(): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.key === CHANGE_KEY || event.key === null) {
      useAuthStore.getState().clearSession()
    }
  }
  window.addEventListener('storage', onStorage)
  return () => window.removeEventListener('storage', onStorage)
}

export async function authenticateBrowserSession<T extends RegisterResponse>(operation: () => Promise<T>): Promise<T> {
  const change = publishChange('signed-out')
  useAuthStore.getState().clearSession()
  const generation = useAuthStore.getState().generation
  const checkCurrent = () => {
    if (currentBrowserSessionChange() !== change || useAuthStore.getState().generation !== generation) {
      throw new DOMException('Another tab changed the session. Sign in again.', 'AbortError')
    }
  }
  return withSessionLock(async () => {
    checkCurrent()
    const result = await operation()
    checkCurrent()
    if ('accessToken' in result) {
      publishChange('active')
      useAuthStore.getState().setSession(result)
    }
    return result
  })
}

export async function signOutBrowserSession(): Promise<void> {
  const change = endBrowserSessionLocally()
  // Wait for any refresh response to replace its cookie before revoking it.
  await withSessionLock(async () => {
    if (currentBrowserSessionChange() === change) await logout()
  })
}

export function endBrowserSessionLocally(): string {
  try {
    return publishChange('signed-out')
  } finally {
    useAuthStore.getState().clearSession()
  }
}

export async function resetBrowserPassword(token: string, password: string): Promise<{ message: string }> {
  const change = currentBrowserSessionChange()
  return withSessionLock(async () => {
    const result = await resetPassword(token, password)
    if (currentBrowserSessionChange() === change) endBrowserSessionLocally()
    return result
  })
}

export async function revokeBrowserSession(id: string): Promise<void> {
  const generation = useAuthStore.getState().generation
  const change = currentBrowserSessionChange()
  const attempt = () => withSessionLock(async () => {
    const checkCurrent = () => {
      if (generation !== useAuthStore.getState().generation || change !== currentBrowserSessionChange()) {
        throw new DOMException('The account changed while signing out the device.', 'AbortError')
      }
    }
    checkCurrent()
    await revokeSession(id)
    checkCurrent()
  })
  try {
    await attempt()
  } catch (error) {
    // A refresh needs the same lock, so release it before renewing and retrying.
    if (!(error instanceof ApiError) || error.status !== 401 || !await restoreBrowserSession()) throw error
    await attempt()
  }
}

async function withSessionLock<T>(operation: () => Promise<T>): Promise<T> {
  if (!navigator.locks) {
    throw new Error('Use a current browser over HTTPS to securely manage your session.')
  }
  return navigator.locks.request('chanter-browser-session', operation)
}

export function restoreBrowserSession(): Promise<boolean> {
  refreshInFlight ??= refreshBrowserSession().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function refreshBrowserSession(): Promise<boolean> {
  const { generation, user } = useAuthStore.getState()
  const change = currentBrowserSessionChange()
  const stillCurrent = () => useAuthStore.getState().generation === generation && currentBrowserSessionChange() === change
  try {
    return await withSessionLock(async () => {
      if (!stillCurrent() || isSignedOut()) {
        if (stillCurrent()) useAuthStore.getState().clearSession()
        return false
      }
      const session = await refreshSession()
      if (!stillCurrent()) return false
      if (!session || (user && user.id !== session.user.id)) {
        useAuthStore.getState().clearSession()
        return false
      }
      useAuthStore.getState().setSession(session)
      return true
    })
  } catch (error) {
    if (!stillCurrent()) return false
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      publishChange('signed-out')
      useAuthStore.getState().clearSession()
    } else if (!user) {
      useAuthStore.setState({ status: 'unavailable' })
    }
    return false
  }
}
