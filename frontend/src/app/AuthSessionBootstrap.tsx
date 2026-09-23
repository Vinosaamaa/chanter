import { useEffect, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { restoreBrowserSession, synchronizeBrowserSession } from '../features/auth/browser-session'
import { useAuthStore } from '../stores/auth-store'

export function AuthSessionBootstrap({ children }: { children: ReactNode }) {
  const status = useAuthStore((state) => state.status)
  const { pathname } = useLocation()
  const deletionReceipt = pathname.startsWith('/account-deletion/')

  useEffect(() => {
    const stop = synchronizeBrowserSession()
    return stop
  }, [])
  useEffect(() => {
    // A receipt has independent read-only authority and must survive account revocation.
    if (!deletionReceipt && useAuthStore.getState().status === 'restoring') void restoreBrowserSession()
  }, [deletionReceipt])

  if (pathname !== '/sign-in' && pathname !== '/app' && !pathname.startsWith('/app/')) return children

  if (status === 'restoring') {
    return <main className="v2-auth-page compact-auth"><p role="status">Restoring your session…</p></main>
  }
  if (status === 'unavailable') {
    return (
      <main className="v2-auth-page compact-auth">
        <section className="v2-auth-panel solo"><div className="v2-auth-card">
          <h1>Unable to restore your session</h1>
          <p role="alert">Check your connection and try again. Secure sessions require a current browser over HTTPS.</p>
          <button className="v2-primary-button" type="button" onClick={() => {
            useAuthStore.setState({ status: 'restoring' })
            void restoreBrowserSession()
          }}>Try again</button>
        </div></section>
      </main>
    )
  }
  return children
}
