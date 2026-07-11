import type { ReactNode } from 'react'

import { useGlobalSearch } from '../../global-search/hooks/use-global-search'
import { cn } from '../../../lib/cn'

type ChannelHeaderProps = {
  channelName: string
  courseTitle?: string | null
  description?: string | null
  trailing?: ReactNode
}

export function ChannelHeader({
  channelName,
  courseTitle,
  description,
  trailing,
}: ChannelHeaderProps) {
  const { openSearch } = useGlobalSearch()

  return (
    <header className="border-b border-app-border px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          {courseTitle ? (
            <p className="truncate text-xs text-app-muted">
              {courseTitle} <span aria-hidden>›</span> #{channelName}
            </p>
          ) : (
            <p className="text-xs text-app-muted">Study Server channel</p>
          )}
          <h2 className="mt-0.5 truncate text-base font-semibold text-app-text">#{channelName}</h2>
          {description ? <p className="mt-1 text-sm text-app-muted">{description}</p> : null}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <HeaderIconButton label="Search this Study Server" onClick={openSearch}>
            ⌕
          </HeaderIconButton>
          {trailing}
        </div>
      </div>
    </header>
  )
}

function HeaderIconButton({
  label,
  onClick,
  children,
}: {
  label: string
  onClick?: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={cn(
        'inline-flex h-8 w-8 items-center justify-center rounded-md border border-app-border',
        'bg-app-surface text-sm text-app-muted transition-colors hover:text-app-text',
      )}
    >
      {children}
    </button>
  )
}
