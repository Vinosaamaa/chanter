import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Check, ChevronLeft, ChevronRight, UsersRound } from 'lucide-react'
import { useMemo, useRef, useState, type KeyboardEvent } from 'react'
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
  const [focusedDay, setFocusedDay] = useState(today)
  const dayButtons = useRef(new Map<string, HTMLButtonElement>())
  const [filter, setFilter] = useState<CalendarFilter>('All')
  const [actionError, setActionError] = useState<string | null>(null)
  const [deepLinkApplied, setDeepLinkApplied] = useState<string | null>(null)
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

  const items = useMemo(
    () => calendarQuery.data?.items ?? [],
    [calendarQuery.data?.items],
  )
  const notes = calendarQuery.data?.notes ?? []

  const deepLinkedMatch = useMemo(() => {
    if (!deepLinkedEventId) return null
    return items.find((item) => item.sourceId === deepLinkedEventId && item.type === 'EVENT') ?? null
  }, [deepLinkedEventId, items])

  if (deepLinkedMatch && deepLinkApplied !== deepLinkedMatch.sourceId) {
    const start = new Date(deepLinkedMatch.startsAt)
    setDeepLinkApplied(deepLinkedMatch.sourceId)
    setSelectedDay(startOfDay(start))
    setFocusedDay(startOfDay(start))
    setViewMonth(new Date(start.getFullYear(), start.getMonth(), 1))
  }

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
    setFocusedDay(today)
  }

  const shiftMonth = (delta: number) => {
    const next = new Date(viewMonth.getFullYear(), viewMonth.getMonth() + delta, 1)
    setViewMonth(next)
    setFocusedDay(next)
  }

  const moveDayFocus = (event: KeyboardEvent<HTMLButtonElement>, date: Date) => {
    const offsets: Record<string, number> = {
      ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7,
      Home: -date.getDay(), End: 6 - date.getDay(),
    }
    let next: Date
    if (event.key in offsets) next = addDays(date, offsets[event.key])
    else if (event.key === 'PageUp' || event.key === 'PageDown') {
      const month = date.getMonth() + (event.key === 'PageUp' ? -1 : 1)
      const lastDay = new Date(date.getFullYear(), month + 1, 0).getDate()
      next = new Date(date.getFullYear(), month, Math.min(date.getDate(), lastDay))
    } else return
    event.preventDefault()
    setFocusedDay(next)
    if (next.getMonth() !== viewMonth.getMonth() || next.getFullYear() !== viewMonth.getFullYear()) {
      setViewMonth(new Date(next.getFullYear(), next.getMonth(), 1))
    }
    requestAnimationFrame(() => dayButtons.current.get(next.toISOString())?.focus())
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

        <div className="v2-chip-row calendar-filters" role="group" aria-label="Calendar filters">
          {(['All', 'Office hours', 'Events', 'Deadlines', 'Going'] as CalendarFilter[]).map((item) => (
            <button
              type="button"
              key={item}
              className={filter === item ? 'active' : undefined}
              aria-pressed={filter === item}
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

        <p id="calendar-keyboard-help" className="sr-only">Use arrow keys to move by day or week, Home and End for the week, and Page Up or Page Down for the month. Press Enter to show the selected day's schedule.</p>
        <div className="calendar-month-grid" role="grid" aria-label={monthLabel} aria-describedby="calendar-keyboard-help">
          {Array.from({ length: 6 }, (_, week) => <div role="row" className="calendar-week" key={week}>
          {cells.slice(week * 7, week * 7 + 7).map(({ date, inMonth }) => {
            const dayItems = itemsOnDay(items, date)
            const dot = calendarQuery.isLoading ? undefined : dotClassForItems(dayItems)
            const selected = sameDay(date, selectedDay)
            return (
              <div role="gridcell" aria-selected={selected} key={date.toISOString()}>
              <button
                type="button"
                ref={element => { if (element) dayButtons.current.set(date.toISOString(), element); else dayButtons.current.delete(date.toISOString()) }}
                aria-label={`${date.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}, ${dayItems.length} scheduled ${dayItems.length === 1 ? 'item' : 'items'}`}
                aria-current={sameDay(date, today) ? 'date' : undefined}
                tabIndex={sameDay(date, focusedDay) ? 0 : -1}
                onKeyDown={event => moveDayFocus(event, date)}
                className={`${inMonth ? '' : 'muted'} ${selected && inMonth ? 'selected' : ''}`.trim()}
                onClick={() => {
                  if (!inMonth) {
                    setViewMonth(new Date(date.getFullYear(), date.getMonth(), 1))
                  }
                  setSelectedDay(startOfDay(date))
                  setFocusedDay(startOfDay(date))
                }}
              >
                <span>{date.getDate()}</span>
                {dot ? <i className={dot} /> : null}
              </button>
              </div>
            )
          })}
          </div>)}
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
