export function ContextPlaceholder() {
  return (
    <aside className="hidden w-80 shrink-0 flex-col border-l border-app-border bg-app-surface lg:flex">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Context
        </p>
        <h2 className="mt-1 text-sm font-semibold text-app-text">AI & resources</h2>
        <p className="text-xs text-app-muted">Context panels open per channel.</p>
      </div>
      <div className="flex flex-1 items-center justify-center p-4">
        <p className="text-center text-xs text-app-muted">
          Course resources, TA queue, and AI context panels will open in this column.
        </p>
      </div>
    </aside>
  )
}
