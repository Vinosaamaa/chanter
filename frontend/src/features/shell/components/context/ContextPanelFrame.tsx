import type { ReactNode } from 'react'

export function ContextPanelFrame({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string
  title: string
  children: ReactNode
}) {
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">{eyebrow}</p>
        <h2 className="mt-1 text-sm font-semibold text-app-text">{title}</h2>
      </div>
      <div className="flex-1 space-y-4 overflow-y-auto p-4">{children}</div>
    </div>
  )
}

export function ContextWidgetSection({
  title,
  action,
  children,
}: {
  title: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="rounded-lg border border-app-border bg-app-bg p-3">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">{title}</h3>
        {action}
      </div>
      <div className="mt-2">{children}</div>
    </section>
  )
}
