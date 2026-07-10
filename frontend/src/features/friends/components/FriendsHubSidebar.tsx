import { Link, useLocation } from 'react-router-dom'

import { cn } from '../../../lib/cn'

import { usePendingFriendRequestCount } from '../hooks/use-friend-requests-queries'

const navItems = [
  { label: 'Friends', to: '/app/friends', exact: true },
  { label: 'Pending Requests', to: '/app/friends/requests', exact: false },
] as const

export function FriendsHubSidebar() {
  const location = useLocation()
  const { incomingCount } = usePendingFriendRequestCount()

  return (
    <aside className="flex w-72 shrink-0 flex-col border-r border-app-border bg-app-elevated">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">Social</p>
        <h1 className="mt-1 text-base font-semibold text-app-text">Friends Hub</h1>
      </div>

      <nav className="flex flex-col gap-1 p-2" aria-label="Friends Hub">
        {navItems.map((item) => {
          const isActive = item.exact
            ? location.pathname === item.to
            : location.pathname.startsWith(item.to)

          return (
            <Link
              key={item.to}
              to={item.to}
              aria-current={isActive ? 'page' : undefined}
              className={cn(
                'flex items-center justify-between rounded-lg px-3 py-2 text-sm transition-colors',
                isActive
                  ? 'bg-app-surface font-medium text-app-text'
                  : 'text-app-muted hover:bg-app-surface hover:text-app-text',
              )}
            >
              <span>{item.label}</span>
              {item.to === '/app/friends/requests' && incomingCount > 0 ? (
                <span className="rounded-full bg-app-accent px-2 py-0.5 text-xs font-medium text-white">
                  {incomingCount}
                </span>
              ) : null}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
