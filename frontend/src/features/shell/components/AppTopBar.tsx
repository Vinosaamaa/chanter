import { Link, useLocation, useNavigate } from 'react-router-dom'

import { logout as logoutApi } from '../../auth/auth-api'
import { useGlobalSearch } from '../../global-search/hooks/use-global-search'
import { usePendingFriendRequestCount } from '../../friends/hooks/use-friend-requests-queries'
import { cn } from '../../../lib/cn'
import { useAuthStore } from '../../../stores/auth-store'
import { useThemeStore } from '../../../stores/theme-store'

const topNavItems = [
  { id: 'friends', label: 'Friends', to: '/app/friends', icon: '👥', matchPrefix: true },
  {
    id: 'dashboard',
    label: 'Instructor Dashboard',
    to: '/app/instructor-dashboard',
    icon: '📊',
    matchPrefix: false,
  },
] as const

export function AppTopBar() {
  const navigate = useNavigate()
  const location = useLocation()
  const { openSearch } = useGlobalSearch()
  const { incomingCount } = usePendingFriendRequestCount()
  const user = useAuthStore((state) => state.user)
  const clearSession = useAuthStore((state) => state.clearSession)
  const theme = useThemeStore((state) => state.theme)
  const toggleTheme = useThemeStore((state) => state.toggleTheme)

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
      <div className="flex items-center gap-3">
        <Link to="/app" className="text-sm font-semibold tracking-tight text-app-text hover:text-app-accent">
          Chanter
        </Link>
        <nav className="flex items-center gap-1">
          {topNavItems.map((item) => {
            const isActive = item.matchPrefix
              ? location.pathname.startsWith(item.to)
              : location.pathname === item.to

            return (
              <Link
                key={item.id}
                to={item.to}
                title={item.label}
                aria-label={item.label}
                aria-current={isActive ? 'page' : undefined}
                className={cn(
                  'relative inline-flex h-8 w-8 items-center justify-center rounded-md text-base transition-colors',
                  isActive
                    ? 'bg-app-surface text-app-text'
                    : 'text-app-muted hover:bg-app-surface hover:text-app-text',
                )}
              >
                <span aria-hidden>{item.icon}</span>
                {item.id === 'friends' && incomingCount > 0 ? (
                  <span className="absolute -right-1 -top-1 rounded-full bg-app-accent px-1 py-0.5 text-[9px] font-semibold leading-none text-white">
                    {incomingCount}
                  </span>
                ) : null}
              </Link>
            )
          })}
        </nav>
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={toggleTheme}
          title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-app-border text-sm text-app-muted hover:text-app-text"
        >
          <span aria-hidden>{theme === 'dark' ? '☀' : '☾'}</span>
        </button>
        <button
          type="button"
          onClick={openSearch}
          className="flex items-center gap-2 rounded-lg border border-app-border bg-app-bg px-3 py-1.5 text-sm text-app-muted hover:text-app-text"
        >
          <span aria-hidden>⌕</span>
          <span className="hidden sm:inline">Search</span>
          <kbd className="hidden rounded border border-app-border px-1.5 py-0.5 text-[10px] sm:inline">⌘K</kbd>
        </button>
        <button
          type="button"
          title="Help"
          aria-label="Help"
          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-app-border text-sm text-app-muted hover:text-app-text"
        >
          ?
        </button>
        <div className="hidden items-center gap-2 sm:flex">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-app-accent text-xs font-semibold text-white">
            {(user?.displayName ?? user?.email ?? '?').slice(0, 1).toUpperCase()}
          </span>
          <span className="max-w-32 truncate text-xs text-app-muted">
            {user?.displayName ?? user?.email ?? 'Signed in'}
          </span>
        </div>
        <button
          type="button"
          className="rounded-md px-2 py-1 text-xs text-app-accent hover:text-app-accent-hover"
          onClick={handleSignOut}
        >
          Sign out
        </button>
      </div>
    </header>
  )
}
