import { useMemo, useState } from 'react'
import { Check, ExternalLink } from 'lucide-react'
import { Link } from 'react-router-dom'

import {
  useMarkNotificationDoneMutation,
  useMarkNotificationReadMutation,
  useNotificationsQuery,
} from '../../inbox/hooks/use-inbox-queries'
import type { InboxNotification, NotificationFilter as ApiFilter } from '../../inbox/types'

type InboxFilter = 'All' | 'Mentions' | 'Announcements'

const FILTER_TO_API: Record<InboxFilter, ApiFilter> = {
  All: 'ALL',
  Mentions: 'MENTIONS',
  Announcements: 'ANNOUNCEMENTS',
}

function formatRelativeTime(iso: string): string {
  const created = new Date(iso).getTime()
  if (Number.isNaN(created)) return ''
  const deltaMs = Date.now() - created
  const minutes = Math.floor(deltaMs / 60_000)
  if (minutes < 1) return 'now'
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h`
  const days = Math.floor(hours / 24)
  if (days === 1) return 'yesterday'
  return `${days}d`
}

function toneFor(notification: InboxNotification): string {
  if (notification.filterBucket === 'ANNOUNCEMENTS') return 'blue'
  if (notification.filterBucket === 'MENTIONS') return 'purple'
  return 'green'
}

function isYesterday(iso: string): boolean {
  const created = new Date(iso)
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfYesterday = startOfToday - 86_400_000
  const createdMs = created.getTime()
  return createdMs >= startOfYesterday && createdMs < startOfToday
}

export function InboxPage() {
  const [filter, setFilter] = useState<InboxFilter>('All')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const notificationsQuery = useNotificationsQuery(FILTER_TO_API[filter], 'OPEN')
  const markRead = useMarkNotificationReadMutation()
  const markDone = useMarkNotificationDoneMutation()

  const notifications = useMemo(
    () => notificationsQuery.data?.notifications ?? [],
    [notificationsQuery.data?.notifications],
  )

  const selected = useMemo(() => {
    if (notifications.length === 0) return null
    return notifications.find((item) => item.id === selectedId) ?? notifications[0] ?? null
  }, [notifications, selectedId])

  const activeId = selected?.id ?? null

  const todayItems = notifications.filter((item) => !isYesterday(item.createdAt))
  const yesterdayItems = notifications.filter((item) => isYesterday(item.createdAt))

  const onSelect = (id: string) => {
    setSelectedId(id)
    const item = notifications.find((notification) => notification.id === id)
    if (item?.unread) {
      markRead.mutate(id)
    }
  }

  const onMarkDone = () => {
    if (!selected) return
    markDone.mutate(selected.id, {
      onSuccess: () => {
        setSelectedId(null)
      },
    })
  }

  return (
    <section className="v2-workspace-page inbox-page" aria-label="Inbox">
      <aside className="inbox-thread-pane">
        <h1>Inbox</h1>
        <div className="v2-chip-row" role="tablist" aria-label="Inbox filters">
          {(['All', 'Mentions', 'Announcements'] as InboxFilter[]).map((item) => (
            <button
              key={item}
              type="button"
              className={filter === item ? 'active' : undefined}
              onClick={() => {
                setFilter(item)
                setSelectedId(null)
              }}
            >
              {item}
            </button>
          ))}
        </div>

        {notificationsQuery.isLoading ? <p className="v2-section-label">Loading…</p> : null}
        {notificationsQuery.isError ? <p className="v2-section-label">Could not load inbox.</p> : null}
        {!notificationsQuery.isLoading && !notificationsQuery.isError && notifications.length === 0 ? (
          <p className="v2-section-label">No open notifications.</p>
        ) : null}

        {todayItems.length > 0 ? (
          <>
            <p className="v2-section-label">Today</p>
            <div className="inbox-thread-list">
              {todayItems.map((thread) => (
                <NotificationRow
                  key={thread.id}
                  thread={thread}
                  active={activeId === thread.id}
                  onSelect={onSelect}
                />
              ))}
            </div>
          </>
        ) : null}

        {yesterdayItems.length > 0 ? (
          <>
            <p className="v2-section-label yesterday">Yesterday</p>
            <div className="inbox-thread-list">
              {yesterdayItems.map((thread) => (
                <NotificationRow
                  key={thread.id}
                  thread={thread}
                  active={activeId === thread.id}
                  onSelect={onSelect}
                />
              ))}
            </div>
          </>
        ) : null}
      </aside>

      <div className="inbox-reading-pane">
        {selected ? (
          <>
            <header className="reading-header">
              <div>
                <h2>{selected.title}</h2>
                <p>
                  {selected.courseLabel ? <span>{selected.courseLabel}</span> : <span>Inbox</span>}
                  {' · '}
                  {selected.filterBucket === 'ANNOUNCEMENTS'
                    ? 'Announcements'
                    : selected.filterBucket === 'MENTIONS'
                      ? 'Mentions'
                      : 'Other'}
                </p>
              </div>
              <div>
                {selected.href ? (
                  <Link to={selected.href} className="v2-outline-button">
                    <ExternalLink size={17} /> Open in course
                  </Link>
                ) : null}
                <button
                  type="button"
                  className={selected.doneAt ? 'v2-success-button' : 'v2-outline-button'}
                  onClick={onMarkDone}
                  disabled={markDone.isPending || Boolean(selected.doneAt)}
                >
                  <Check size={18} /> {selected.doneAt ? 'Done' : 'Mark done'}
                </button>
              </div>
            </header>

            <div className="reading-thread">
              <article className="inbox-message-card compact">
                <div>
                  <p className="message-author">
                    <strong>{selected.title}</strong>
                    <time>{formatRelativeTime(selected.createdAt)}</time>
                  </p>
                  <p>{selected.bodyPreview ?? 'No additional details.'}</p>
                </div>
              </article>
            </div>
          </>
        ) : (
          <div className="reading-thread">
            <p className="v2-section-label">Select a notification to read it.</p>
          </div>
        )}
      </div>
    </section>
  )
}

function NotificationRow({
  thread,
  active,
  onSelect,
}: {
  thread: InboxNotification
  active: boolean
  onSelect: (id: string) => void
}) {
  const course = thread.courseLabel ? `[${thread.courseLabel}] ` : ''
  return (
    <button
      type="button"
      className={active ? 'active' : undefined}
      onClick={() => onSelect(thread.id)}
    >
      <i className={`thread-dot ${toneFor(thread)}${thread.unread ? '' : ' read'}`} />
      <span>
        <strong>{course}</strong>
        {thread.title}
      </span>
      <time>{formatRelativeTime(thread.createdAt)}</time>
    </button>
  )
}
