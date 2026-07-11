import { Link } from 'react-router-dom'

import { useTaQueuePanel } from '../../../../support-operations/hooks/use-ta-queue-panel'
import { supportOperationPath } from '../../../shell-routes'

import { ContextWidgetSection } from '../ContextPanelFrame'

function formatWaitMinutes(createdAt: string): string {
  const created = new Date(createdAt).getTime()
  if (Number.isNaN(created)) {
    return '—'
  }
  const minutes = Math.max(1, Math.round((Date.now() - created) / 60_000))
  return `${minutes}m`
}

export function TaQueueSummaryWidget({
  serverId,
  courseId,
  cohortId,
}: {
  serverId: string
  courseId: string
  cohortId: string | undefined
}) {
  const queue = useTaQueuePanel(cohortId)
  const openItems = queue.items.filter((item) => item.status !== 'RESOLVED').slice(0, 3)

  return (
    <ContextWidgetSection
      title="TA queue"
      action={
        <Link
          to={supportOperationPath(serverId, courseId, 'ta-queue')}
          className="text-[11px] font-medium text-app-accent hover:text-app-accent-hover"
        >
          View all
        </Link>
      }
    >
      {queue.isLoading ? (
        <p className="text-xs text-app-muted">Loading queue…</p>
      ) : queue.error ? (
        <p className="text-xs text-app-muted">{queue.error}</p>
      ) : !queue.canManage ? (
        <p className="text-xs text-app-muted">
          Post in <strong>#questions</strong> and use <strong>Add to TA Queue</strong> when you need
          human help.
        </p>
      ) : openItems.length === 0 ? (
        <p className="text-xs text-app-muted">No learners waiting in the TA queue.</p>
      ) : (
        <ul className="space-y-2">
          {openItems.map((item) => (
            <li key={item.id} className="rounded-md border border-app-border bg-app-surface px-2 py-2">
              <p className="truncate text-xs font-medium text-app-text">{item.body}</p>
              <p className="mt-1 text-[11px] text-app-muted">
                {item.status.replaceAll('_', ' ')} · {formatWaitMinutes(item.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </ContextWidgetSection>
  )
}
