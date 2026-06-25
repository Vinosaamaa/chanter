import { Link, Outlet, useNavigate } from 'react-router-dom'

import { logout as logoutApi } from '../../auth/auth-api'
import { cn } from '../../../lib/cn'
import { useAuthStore } from '../../../stores/auth-store'

const navItems = [
  { label: 'Home', to: '/app' },
  { label: 'My courses', to: '/app/courses' },
  { label: 'Friends', to: '/app/friends' },
]

export function AppShellLayout() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const refreshToken = useAuthStore((state) => state.refreshToken)
  const clearSession = useAuthStore((state) => state.clearSession)

  const handleSignOut = async () => {
    if (refreshToken) {
      try {
        await logoutApi(refreshToken)
      } catch {
        // Local session clear still runs if the network call fails.
      }
    }
    clearSession()
    navigate('/sign-in', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-app-bg text-app-text">
      <aside className="flex w-72 shrink-0 flex-col border-r border-app-border bg-app-surface">
        <div className="border-b border-app-border px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-app-accent">
            Study Server
          </p>
          <h1 className="mt-1 text-lg font-semibold">Chanter</h1>
          <p className="text-sm text-app-muted">Enrollment-scoped sidebar placeholder (#50)</p>
        </div>
        <nav className="flex flex-1 flex-col gap-1 p-3">
          {navItems.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={cn(
                'rounded-md px-3 py-2 text-sm text-app-muted transition-colors',
                'hover:bg-app-elevated hover:text-app-text',
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="border-t border-app-border p-3 text-xs text-app-muted">
          <Link className="hover:text-app-text" to="/dev/demo">
            API demo harness
          </Link>
        </div>
      </aside>
      <main className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-app-border bg-app-elevated px-6 py-3">
          <div>
            <p className="text-sm font-medium text-app-text">App shell</p>
            <p className="text-xs text-app-muted">
              Signed in as {user?.displayName ?? user?.email ?? 'unknown user'}
            </p>
          </div>
          <button
            type="button"
            className="text-sm text-app-accent hover:text-app-accent-hover"
            onClick={handleSignOut}
          >
            Sign out
          </button>
        </header>
        <div className="flex-1 overflow-auto p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
