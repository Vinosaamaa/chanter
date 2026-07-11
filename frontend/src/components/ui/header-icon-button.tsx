import type { ReactNode } from 'react'

import { cn } from '../../lib/cn'

export function HeaderIconButton({
  label,
  title,
  onClick,
  children,
  className,
  disabled = false,
}: {
  label: string
  title?: string
  onClick?: () => void
  children: ReactNode
  className?: string
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={title ?? label}
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'inline-flex h-8 w-8 items-center justify-center rounded-md border border-app-border',
        'text-sm text-app-muted transition-colors hover:text-app-text',
        className,
      )}
    >
      {children}
    </button>
  )
}
