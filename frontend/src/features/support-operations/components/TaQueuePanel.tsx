import { cn } from '../../../lib/cn'
import { useTaQueuePanel } from '../hooks/use-ta-queue-panel'

type TaQueuePanelProps = {
  courseTitle: string
  cohortName: string
  cohortId: string
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function statusLabel(status: string): string {
  switch (status) {
    case 'OPEN':
      return 'Open'
    case 'PICKED_UP':
      return 'Picked up'
    case 'RESOLVED':
      return 'Resolved'
    default:
      return status
  }
}

export function TaQueuePanel({ courseTitle, cohortName, cohortId }: TaQueuePanelProps) {
  const queue = useTaQueuePanel(cohortId)

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <header className="border-b border-app-border px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Support operations
        </p>
        <h2 className="mt-1 text-xl font-semibold text-app-text">TA queue</h2>
        <p className="mt-1 text-sm text-app-muted">
          {courseTitle} · {cohortName}
        </p>
      </header>

      <div className="flex-1 overflow-y-auto p-4">
        {queue.isLoading && <p className="text-sm text-app-muted">Loading TA queue…</p>}

        {queue.accessDenied && (
          <p className="rounded-lg border border-app-border bg-app-surface px-4 py-3 text-sm text-app-muted">
            You do not have permission to manage the TA queue for this cohort. Instructors and TAs
            can pick up learner handoffs here.
          </p>
        )}

        {queue.error && (
          <p className="mb-3 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
            {queue.error}
          </p>
        )}

        {queue.actionMessage && (
          <p className="mb-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
            {queue.actionMessage}
          </p>
        )}

        {!queue.isLoading && queue.canManage && (
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="text-sm text-app-muted">
              {queue.items.length === 0
                ? 'No open queue items.'
                : `${queue.items.length} open item${queue.items.length === 1 ? '' : 's'}.`}
            </p>
            <button
              type="button"
              onClick={() => void queue.refresh()}
              className="rounded-lg border border-app-border px-3 py-1.5 text-xs text-app-muted hover:bg-app-elevated hover:text-app-text"
            >
              Refresh
            </button>
          </div>
        )}

        <ul className="flex flex-col gap-3">
          {queue.items.map((item) => (
            <li
              key={item.id}
              className="rounded-xl border border-app-border bg-app-surface p-4"
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide',
                        item.status === 'OPEN'
                          ? 'bg-amber-500/15 text-amber-200'
                          : 'bg-sky-500/15 text-sky-200',
                      )}
                    >
                      {statusLabel(item.status)}
                    </span>
                    <span className="text-xs text-app-muted">
                      {formatTimestamp(item.createdAt)}
                    </span>
                  </div>
                  <p className="mt-2 text-sm text-app-text">{item.body}</p>
                  <p className="mt-2 text-xs text-app-muted">
                    Learner {item.learnerUserId.slice(0, 8)}…
                  </p>
                </div>

                <div className="flex shrink-0 flex-wrap gap-2">
                  {item.status === 'OPEN' && (
                    <button
                      type="button"
                      disabled={queue.actingItemId === item.id}
                      onClick={() => void queue.pickupItem(item.id)}
                      className="rounded-lg bg-app-accent px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
                    >
                      Pick up
                    </button>
                  )}
                  {item.status === 'PICKED_UP' && (
                    <button
                      type="button"
                      disabled={queue.actingItemId === item.id}
                      onClick={() => void queue.resolveItem(item.id)}
                      className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
                    >
                      Resolve
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
