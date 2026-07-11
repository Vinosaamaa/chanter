import { useRef, type ReactNode } from 'react'

import { usePanelResize } from '../../hooks/use-panel-resize'
import { cn } from '../../lib/cn'

type ResizableShellPanelProps = {
  side: 'left' | 'right'
  width: number
  collapsed: boolean
  minWidth: number
  maxWidth: number
  onWidthChange: (width: number) => void
  onToggleCollapsed: () => void
  collapseLabel: string
  expandLabel: string
  className?: string
  children: ReactNode
}

export function ResizableShellPanel({
  side,
  width,
  collapsed,
  minWidth,
  maxWidth,
  onWidthChange,
  onToggleCollapsed,
  collapseLabel,
  expandLabel,
  className,
  children,
}: ResizableShellPanelProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const { onPointerDown, onSeparatorKeyDown } = usePanelResize({
    getPanel: () => panelRef.current,
    side,
    onWidthChange,
    minWidth,
    maxWidth,
  })

  const edgePosition = side === 'left' ? 'right-0 translate-x-1/2' : 'left-0 -translate-x-1/2'
  const collapseChevron = side === 'left' ? '‹' : '›'
  const expandChevron = side === 'left' ? '›' : '‹'
  const collapseButtonClassName = cn(
    'absolute top-1/2 z-20 flex h-10 w-3 -translate-y-1/2 items-center justify-center rounded-full border border-app-border/80 bg-app-elevated text-[10px] text-app-muted shadow-sm transition-all hover:text-app-text focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-accent',
    edgePosition,
  )

  if (collapsed) {
    return (
      <div
        className={cn(
          'group/edge relative flex w-3 shrink-0 items-stretch',
          side === 'left' ? 'border-r border-app-border' : 'border-l border-app-border',
          className,
        )}
      >
        <button
          type="button"
          onClick={onToggleCollapsed}
          aria-label={expandLabel}
          title={expandLabel}
          className={cn(collapseButtonClassName, 'opacity-70 hover:opacity-100')}
        >
          {expandChevron}
        </button>
      </div>
    )
  }

  return (
    <div
      ref={panelRef}
      data-shell-panel=""
      className={cn('group/panel relative flex shrink-0 flex-col', className)}
      style={{ width }}
    >
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">{children}</div>
      <div
        className={cn(
          'group/edge absolute top-0 z-10 h-full w-3',
          side === 'left' ? 'right-0' : 'left-0',
        )}
      >
        <div
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize panel"
          aria-valuemin={minWidth}
          aria-valuemax={maxWidth}
          aria-valuenow={width}
          tabIndex={0}
          onPointerDown={onPointerDown}
          onKeyDown={onSeparatorKeyDown}
          onDoubleClick={onToggleCollapsed}
          className="absolute inset-0 cursor-col-resize bg-transparent hover:bg-app-accent/15 focus-visible:bg-app-accent/20 focus-visible:outline-none"
        />
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onToggleCollapsed()
          }}
          aria-label={collapseLabel}
          title={`${collapseLabel} (double-click edge)`}
          className={cn(collapseButtonClassName, 'opacity-0 group-hover/edge:opacity-100')}
        >
          {collapseChevron}
        </button>
      </div>
    </div>
  )
}
