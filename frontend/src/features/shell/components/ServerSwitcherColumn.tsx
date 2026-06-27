import { Link, useParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'
import { useAccessibleStudyServersQuery } from '../hooks/use-shell-queries'

export function ServerSwitcherColumn() {
  const { serverId } = useParams()
  const serversQuery = useAccessibleStudyServersQuery()

  return (
    <aside className="flex w-[72px] shrink-0 flex-col items-center gap-2 border-r border-app-border bg-app-bg py-3">
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
              {initials(server.name)}
            </span>
          </Link>
        )
      })}
    </aside>
  )
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) {
    return '?'
  }
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase()
  }
  return `${words[0][0]}${words[1][0]}`.toUpperCase()
}
