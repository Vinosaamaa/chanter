import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Check, ChevronLeft, ChevronRight, UsersRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { calendarQueryKey, fetchCalendar } from '../../calendar/calendar-api'
import type { CalendarItem } from '../../calendar/calendar-types'
import { upsertCommunityEventRsvp } from '../../community-events/community-events-api'
import { formatUserFacingApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'

type CalendarFilter = 'All' | 'Office hours' | 'Events' | 'Deadlines' | 'Going'

const FILTER_TO_TYPES: Record<CalendarFilter, string | undefined> = {
  All: undefined,
  'Office hours': 'OFFICE_HOURS',
  Events: 'EVENT',
  Deadlines: 'DEADLINE',
  Going: 'GOING',
}

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as const

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function sameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
  )
}

function monthGrid(viewMonth: Date): { date: Date; inMonth: boolean }[] {
  const first = new Date(viewMonth.getFullYear(), viewMonth.getMonth(), 1)
  const gridStart = addDays(first, -first.getDay())
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(gridStart, index)
    return { date, inMonth: date.getMonth() === viewMonth.getMonth() }
  })
}

function monthBounds(viewMonth: Date): { from: string; to: string } {
  const cells = monthGrid(viewMonth)
  const from = startOfDay(cells[0].date)
  const to = addDays(startOfDay(cells[41].date), 1)
  return { from: from.toISOString(), to: to.toISOString() }
}

function formatMonthLabel(viewMonth: Date): string {
  return viewMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

function formatDayHeading(date: Date): string {
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

function formatUpcomingDate(iso: string): { date: string; day: string } {
  const value = new Date(iso)
  return {
    date: value.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
    day: value.toLocaleDateString(undefined, { weekday: 'short' }),
  }
}

function dotClassForItems(items: CalendarItem[]): string | undefined {
  if (items.some((item) => item.type === 'EVENT' && item.viewerRsvp === 'GOING')) return 'going'
  if (items.some((item) => item.type === 'OFFICE_HOURS')) return 'office'
  if (items.some((item) => item.type === 'DEADLINE')) return 'deadline'
  if (items.some((item) => item.type === 'EVENT')) return 'event'
  return undefined
}

function toneFor(item: CalendarItem): string {
  if (item.type === 'OFFICE_HOURS') return 'blue'
  if (item.type === 'DEADLINE') return 'amber'
  if (item.viewerRsvp === 'GOING') return 'green'
  return 'purple'
}

function itemsOnDay(items: CalendarItem[], day: Date): CalendarItem[] {
  return items.filter((item) => sameDay(new Date(item.startsAt), day))
}

export function CalendarPage() {
  const userId = useAuthStore((state) => state.user?.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const searchQuery = searchParams.get('q') ?? ''
  const deepLinkedEventId = searchParams.get('event')

  const today = useMemo(() => startOfDay(new Date()), [])
  const [viewMonth, setViewMonth] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1))
  const [selectedDay, setSelectedDay] = useState(today)
  const [filter, setFilter] = useState<CalendarFilter>('All')
  const [actionError, setActionError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const range = useMemo(() => monthBounds(viewMonth), [viewMonth])
  const types = FILTER_TO_TYPES[filter]

  const calendarQuery = useQuery({
    queryKey: calendarQueryKey(userId, {
      from: range.from,
      to: range.to,
      types,
      search: searchQuery,
    }),
    queryFn: () =>
      fetchCalendar({
        from: range.from,
        to: range.to,
        types,
        search: searchQuery || undefined,
      }),
    enabled: Boolean(userId),
  })

  const items = calendarQuery.data?.items ?? []
  const notes = calendarQuery.data?.notes ?? []

  const cells = useMemo(() => monthGrid(viewMonth), [viewMonth])
  const selectedDayItems = useMemo(() => itemsOnDay(items, selectedDay), [items, selectedDay])
  const upcomingWeekItems = useMemo(() => {
    const weekEnd = addDays(today, 7)
    return items
      .filter((item) => {
        const start = new Date(item.startsAt)
        return start >= today && start < weekEnd
      })
      .slice(0, 8)
  }, [items, today])

  useEffect(() => {
    if (!deepLinkedEventId) return
    const match = items.find((item) => item.sourceId === deepLinkedEventId && item.type === 'EVENT')
    if (match) {
      setSelectedDay(startOfDay(new Date(match.startsAt)))
      setViewMonth(new Date(new Date(match.startsAt).getFullYear(), new Date(match.startsAt).getMonth(), 1))
    }
  }, [deepLinkedEventId, items])

  const rsvpMutation = useMutation({
    mutationFn: ({
      studyServerId,
      eventId,
      status,
    }: {
      studyServerId: string
      eventId: string
      status: 'GOING' | 'INTERESTED' | 'NOT_GOING'
    }) => upsertCommunityEventRsvp(studyServerId, eventId, status),
    onSuccess: async () => {
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to update RSVP.')),
  })

  const goToday = () => {
    setViewMonth(new Date(today.getFullYear(), today.getMonth(), 1))
    setSelectedDay(today)
  }

  const shiftMonth = (delta: number) => {
    setViewMonth((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1))
  }

  const monthLabel = formatMonthLabel(viewMonth)

  return (
    <section className="v2-workspace-page calendar-page" aria-label="Calendar">
      <div className="calendar-main">
        <header className="calendar-toolbar">
          <div className="month-switcher">
            <button type="button" aria-label="Previous month" onClick={() => shiftMonth(-1)}>
              <ChevronLeft />
            </button>
            <h1>{monthLabel}</h1>
            <button type="button" aria-label="Next month" onClick={() => shiftMonth(1)}>
              <ChevronRight />
            </button>
          </div>
          <button type="button" className="v2-outline-button" onClick={goToday}>
            Today
          </button>
        </header>

        <div className="v2-chip-row calendar-filters" aria-label="Calendar filters">
          {(['All', 'Office hours', 'Events', 'Deadlines', 'Going'] as CalendarFilter[]).map((item) => (
            <button
              type="button"
              key={item}
              className={filter === item ? 'active' : undefined}
              onClick={() => setFilter(item)}
            >
              <i className={`filter-dot ${item.toLowerCase().replace(' ', '-')}`} />
              {item}
            </button>
          ))}
        </div>

        {searchQuery.trim() ? (
          <p className="calendar-search-hint" style={{ color: 'var(--muted)', margin: '0 0 0.75rem' }}>
            Filtering by “{searchQuery.trim()}”
            {' '}
            <button
              type="button"
              style={{ border: 0, background: 'transparent', color: '#5274ff', cursor: 'pointer', padding: 0 }}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.delete('q')
                setSearchParams(next)
              }}
            >
              Clear
            </button>
          </p>
        ) : null}

        <div className="calendar-weekdays" aria-hidden="true">
          {WEEKDAY_LABELS.map((day) => (
            <span key={day}>{day}</span>
          ))}
        </div>

        {calendarQuery.isError ? (
          <p style={{ color: 'var(--muted)', padding: '1rem' }}>
            {formatUserFacingApiError(calendarQuery.error, 'Unable to load calendar.')}
          </p>
        ) : null}

        <div className="calendar-month-grid" role="grid" aria-label={monthLabel}>
          {cells.map(({ date, inMonth }) => {
            const dayItems = itemsOnDay(items, date)
            const dot = calendarQuery.isLoading ? undefined : dotClassForItems(dayItems)
            const selected = sameDay(date, selectedDay)
            return (
              <button
                type="button"
                role="gridcell"
                key={date.toISOString()}
                className={`${inMonth ? '' : 'muted'} ${selected && inMonth ? 'selected' : ''}`.trim()}
                onClick={() => {
                  if (!inMonth) {
                    setViewMonth(new Date(date.getFullYear(), date.getMonth(), 1))
                  }
                  setSelectedDay(startOfDay(date))
                }}
              >
                <span>{date.getDate()}</span>
                {dot ? <i className={dot} /> : null}
              </button>
            )
          })}
        </div>
      </div>

      <aside className="calendar-agenda">
        <h2>
          {formatDayHeading(selectedDay)}
          {' '}
          <small>selected day</small>
        </h2>

        {calendarQuery.isLoading ? (
          <p style={{ color: 'var(--muted)' }}>Loading calendar…</p>
        ) : null}

        {actionError ? <p style={{ color: 'var(--muted)' }}>{actionError}</p> : null}

        {!calendarQuery.isLoading && selectedDayItems.length === 0 ? (
          <p style={{ color: 'var(--muted)' }}>Nothing scheduled this day.</p>
        ) : null}

        {selectedDayItems.map((item) => (
          <AgendaCard
            key={item.id}
            item={item}
            rsvpPending={rsvpMutation.isPending}
            onToggleGoing={() => {
              if (!item.studyServerId) return
              const going = item.viewerRsvp === 'GOING'
              rsvpMutation.mutate({
                studyServerId: item.studyServerId,
                eventId: item.sourceId,
                status: going ? 'NOT_GOING' : 'GOING',
              })
            }}
          />
        ))}

        <h2 className="upcoming-heading">Upcoming this week</h2>
        <div className="upcoming-list">
          {!calendarQuery.isLoading && upcomingWeekItems.length === 0 ? (
            <p style={{ color: 'var(--muted)' }}>No upcoming items this week.</p>
          ) : null}
          {upcomingWeekItems.map((item) => {
            const when = formatUpcomingDate(item.startsAt)
            return (
              <Link key={item.id} to={item.href} className="upcoming-row" style={{ textDecoration: 'none' }}>
                <span className={`agenda-icon ${toneFor(item)}`}>
                  {item.type === 'EVENT' ? <UsersRound /> : <CalendarDays />}
                </span>
                <time>
                  <strong>{when.date}</strong>
                  <small>{when.day}</small>
                </time>
                <p>
                  <strong>{item.type === 'OFFICE_HOURS' ? `${item.title} — ${item.contextLabel}` : item.title}</strong>
                  <small>{item.contextLabel}</small>
                </p>
                {item.type === 'DEADLINE' ? <b>Deadline</b> : null}
              </Link>
            )
          })}
        </div>

        {notes.length > 0 && filter === 'Deadlines' ? (
          <p style={{ color: 'var(--muted)', marginTop: '1rem', fontSize: '0.9rem' }}>{notes[0]}</p>
        ) : null}
      </aside>
    </section>
  )
}

function AgendaCard({
  item,
  rsvpPending,
  onToggleGoing,
}: {
  item: CalendarItem
  rsvpPending: boolean
  onToggleGoing: () => void
}) {
  const going = item.viewerRsvp === 'GOING'
  return (
    <article className="agenda-card">
      <span className={`agenda-icon ${toneFor(item)}`}>
        {item.type === 'EVENT' ? <UsersRound /> : <CalendarDays />}
      </span>
      <div>
        <time>{formatTime(item.startsAt)}</time>
        <p>
          <strong>{item.title}</strong>
          {' '}
          —
          {' '}
          {item.contextLabel}
        </p>
        {item.actionKind === 'JOIN' ? (
          <Link to={item.href}>Join</Link>
        ) : null}
        {item.actionKind === 'RSVP' ? (
          <button
            type="button"
            className={going ? 'going' : undefined}
            disabled={rsvpPending}
            onClick={onToggleGoing}
          >
            Going
            {going ? <Check size={16} /> : null}
          </button>
        ) : null}
        {item.actionKind !== 'JOIN' && item.actionKind !== 'RSVP' ? (
          <Link to={item.href}>Open</Link>
        ) : null}
      </div>
    </article>
  )
}
