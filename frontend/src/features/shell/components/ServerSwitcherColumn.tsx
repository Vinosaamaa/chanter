import { Link, useLocation, useParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'
import { useAccessibleStudyServersQuery } from '../hooks/use-shell-queries'
import { studyServerInitials } from '../study-server-initials'

export function ServerSwitcherColumn() {
  const { serverId } = useParams()
  const location = useLocation()
  const serversQuery = useAccessibleStudyServersQuery()
  const isPickerActive = location.pathname === '/app' || location.pathname === '/app/'

  return (
    <aside className="flex w-[72px] shrink-0 flex-col items-center gap-2 border-r border-app-border bg-app-bg py-3">
      <Link
        to="/app"
        aria-current={isPickerActive ? 'page' : undefined}
        aria-label="All Study Servers"
        title="All Study Servers"
        className={cn(
          'flex h-12 w-12 items-center justify-center rounded-2xl text-lg transition-colors',
          isPickerActive
            ? 'bg-app-accent text-white'
            : 'bg-app-surface text-app-muted hover:bg-app-elevated hover:text-app-text',
        )}
      >
        ⌂
      </Link>

      {serversQuery.isLoading && (
        <p className="px-2 text-center text-[10px] text-app-muted">…</p>
      )}
      {serversQuery.isError && (
        <p className="px-2 text-center text-[10px] text-red-300">!</p>
      )}
      {serversQuery.data?.map((server) => {
        const isActive = server.id === serverId
        const targetPath = `/app/servers/${server.id}/home`

        return (
          <Link
            key={server.id}
            to={targetPath}
            aria-current={isActive ? 'page' : undefined}
            aria-label={isActive ? `${server.name} home` : `Switch to ${server.name}`}
          >
            <span
              className={cn(
                'flex h-12 w-12 items-center justify-center rounded-2xl text-xs font-semibold transition-colors',
                isActive
                  ? 'bg-app-accent text-white'
                  : 'bg-app-surface text-app-muted hover:bg-app-elevated hover:text-app-text',
              )}
              title={server.name}
            >
              {studyServerInitials(server.name)}
            </span>
          </Link>
        )
      })}

      <div className="mt-auto pt-2">
        <Link
          to="/app/onboarding/create-study-server"
          aria-label="Create Study Server"
          title="Create Study Server"
          className="flex h-12 w-12 items-center justify-center rounded-2xl bg-app-surface text-xl text-app-muted transition-colors hover:bg-app-elevated hover:text-app-accent"
        >
          +
        </Link>
      </div>
    </aside>
  )
}
