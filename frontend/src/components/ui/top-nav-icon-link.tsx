import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

import { cn } from '../../lib/cn'

export function TopNavIconLink({
  to,
  label,
  icon,
  isActive,
  badge,
}: {
  to: string
  label: string
  icon: ReactNode
  isActive: boolean
  badge?: ReactNode
}) {
  return (
    <Link
      to={to}
      title={label}
      aria-label={label}
      aria-current={isActive ? 'page' : undefined}
      className={cn(
        'relative inline-flex h-8 w-8 items-center justify-center rounded-md text-base transition-colors',
        isActive
          ? 'bg-app-surface text-app-text'
          : 'text-app-muted hover:bg-app-surface hover:text-app-text',
      )}
    >
      <span aria-hidden>{icon}</span>
      {badge}
    </Link>
  )
}
