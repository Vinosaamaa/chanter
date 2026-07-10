import { Link, useLocation, useNavigate } from 'react-router-dom'

import { logout as logoutApi } from '../../auth/auth-api'
import { useGlobalSearch } from '../../global-search/hooks/use-global-search'
import { usePendingFriendRequestCount } from '../../friends/hooks/use-friend-requests-queries'
import { cn } from '../../../lib/cn'
import { useAuthStore } from '../../../stores/auth-store'

const topLinks = [
  { label: 'Friends', to: '/app/friends', matchPrefix: true },
  { label: 'Instructor Dashboard', to: '/app/instructor-dashboard', matchPrefix: false },
] as const

export function AppTopBar() {
  const navigate = useNavigate()
  const location = useLocation()
  const { openSearch } = useGlobalSearch()
  const { incomingCount } = usePendingFriendRequestCount()
  const user = useAuthStore((state) => state.user)
  const clearSession = useAuthStore((state) => state.clearSession)

  const handleSignOut = async () => {
    const tokenToRevoke = useAuthStore.getState().refreshToken
    if (tokenToRevoke) {
      try {
        await logoutApi(tokenToRevoke)
      } catch {
        // Local session clear still runs if the network call fails.
      }
    }
    clearSession()
    navigate('/sign-in', { replace: true })
  }

  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-app-border bg-app-elevated px-4">
      <nav className="flex items-center gap-1">
        {topLinks.map((item) => {
          const isActive = item.matchPrefix
            ? location.pathname.startsWith(item.to)
            : location.pathname === item.to

          return (
            <Link
              key={item.to}
              to={item.to}
              aria-current={location.pathname === item.to ? 'page' : undefined}
              className={cn(
                'inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm transition-colors',
                isActive
                  ? 'bg-app-surface font-medium text-app-text'
                  : 'text-app-muted hover:bg-app-surface hover:text-app-text',
              )}
            >
              <span>{item.label}</span>
              {item.to === '/app/friends' && incomingCount > 0 ? (
                <span className="rounded-full bg-app-accent px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white">
                  {incomingCount}
                </span>
              ) : null}
            </Link>
          )
        })}
      </nav>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={openSearch}
          className="flex items-center gap-2 rounded-lg border border-app-border bg-app-bg px-3 py-1.5 text-sm text-app-muted hover:text-app-text"
        >
          <span>Search</span>
          <kbd className="hidden rounded border border-app-border px-1.5 py-0.5 text-[10px] sm:inline">⌘K</kbd>
        </button>
        <p className="hidden text-xs text-app-muted sm:block">
          {user?.displayName ?? user?.email ?? 'Signed in'}
        </p>
        <button
          type="button"
          className="text-sm text-app-accent hover:text-app-accent-hover"
          onClick={handleSignOut}
        >
          Sign out
        </button>
      </div>
    </header>
  )
}
