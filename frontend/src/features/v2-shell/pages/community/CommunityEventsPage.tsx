import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Check, MapPin, Plus, Share2, UsersRound, X } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import {
  cancelCommunityEvent,
  communityEventsQueryKey,
  createCommunityEvent,
  downloadCommunityEventIcs,
  fetchCommunityEvents,
  updateCommunityEvent,
  upsertCommunityEventRsvp,
} from '../../../community-events/community-events-api'
import type {
  CommunityEvent,
  CommunityEventFilter,
  CreateCommunityEventInput,
} from '../../../community-events/community-event-types'
import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { useV2Community } from '../../layouts/v2-community-context'

const FILTERS: { label: string; value: CommunityEventFilter }[] = [
  { label: 'Upcoming', value: 'UPCOMING' },
  { label: 'Past', value: 'PAST' },
  { label: 'Going', value: 'GOING' },
]

function formatEventWhen(startsAt: string, endsAt: string): { date: string; time: string } {
  const start = new Date(startsAt)
  const end = new Date(endsAt)
  const date = start.toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
  const time = `${start.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })} – ${end.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })}`
  return { date, time }
}

function toLocalInputValue(iso: string): string {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fromLocalInputValue(value: string): string {
  return new Date(value).toISOString()
}

export function CommunityEventsPage() {
  const { serverId, studyServerCapabilities } = useV2Community()
  const canManageEvents = studyServerCapabilities?.canManageEvents ?? false
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [filter, setFilter] = useState<CommunityEventFilter>('UPCOMING')
  const deepLinkedEventId = searchParams.get('event')
  const [selectedId, setSelectedId] = useState<string | null>(deepLinkedEventId)
  const activeSelectedId = deepLinkedEventId ?? selectedId
  const [editing, setEditing] = useState<CommunityEvent | null | undefined>(undefined)
  const [actionError, setActionError] = useState<string | null>(null)

  const eventsQuery = useQuery({
    queryKey: communityEventsQueryKey(serverId, filter),
    queryFn: () => fetchCommunityEvents(serverId, filter),
    enabled: Boolean(serverId),
  })

  const events = useMemo(() => eventsQuery.data?.events ?? [], [eventsQuery.data?.events])
  const selected = useMemo(
    () => events.find((event) => event.id === activeSelectedId) ?? null,
    [events, activeSelectedId],
  )
  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['community-events', serverId] })
  }

  const rsvpMutation = useMutation({
    mutationFn: ({ eventId, status }: { eventId: string; status: 'GOING' | 'INTERESTED' | 'NOT_GOING' }) =>
      upsertCommunityEventRsvp(serverId, eventId, status),
    onSuccess: async () => {
      setActionError(null)
      await invalidate()
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to update RSVP.')),
  })

  const saveMutation = useMutation({
    mutationFn: async ({
      eventId,
      input,
    }: {
      eventId?: string
      input: CreateCommunityEventInput
    }) => {
      if (eventId) {
        return updateCommunityEvent(serverId, eventId, input)
      }
      return createCommunityEvent(serverId, input)
    },
    onSuccess: async () => {
      setEditing(undefined)
      setActionError(null)
      await invalidate()
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to save event.')),
  })

  const cancelMutation = useMutation({
    mutationFn: (eventId: string) => cancelCommunityEvent(serverId, eventId),
    onSuccess: async () => {
      setSelectedId(null)
      setActionError(null)
      await invalidate()
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to cancel event.')),
  })

  const openEvent = (eventId: string) => {
    setSelectedId(eventId)
    setSearchParams({ event: eventId })
  }

  const closeEvent = () => {
    setSelectedId(null)
    setSearchParams({})
  }

  return (
    <div className="community-events-page">
      <div className="community-event-toolbar">
        <div className="event-filters">
          {FILTERS.map((item) => (
            <button
              type="button"
              key={item.value}
              className={filter === item.value ? 'active' : undefined}
              onClick={() => setFilter(item.value)}
            >
              {item.label}
            </button>
          ))}
        </div>
        {canManageEvents ? (
          <button type="button" className="v2-primary-button" onClick={() => setEditing(null)}>
            <Plus />
            Create event
          </button>
        ) : null}
      </div>

      {actionError ? <p className="v2-inline-error" role="alert">{actionError}</p> : null}
      {eventsQuery.isLoading ? <p>Loading events…</p> : null}
      {eventsQuery.isError ? (
        <p className="v2-inline-error" role="alert">
          {formatUserFacingApiError(eventsQuery.error, 'Unable to load events.')}
          <button type="button" onClick={() => void eventsQuery.refetch()}>
            Retry
          </button>
        </p>
      ) : null}
      {!eventsQuery.isLoading && !eventsQuery.isError && events.length === 0 ? (
        <p>No events match this filter yet.</p>
      ) : null}

      <div className="event-list">
        {events.map((event) => {
          const when = formatEventWhen(event.startsAt, event.endsAt)
          const going = event.viewerRsvp === 'GOING'
          return (
            <article key={event.id} onClick={() => openEvent(event.id)}>
              <span>
                <CalendarDays />
              </span>
              <div>
                <small>
                  {when.date} · {when.time}
                  {event.status === 'CANCELLED' ? ' · Cancelled' : ''}
                </small>
                <h2>{event.title}</h2>
                <p>
                  <MapPin />
                  {event.location || 'Location TBA'}
                  <b>
                    <UsersRound />
                    {event.visibility === 'HUB' ? 'Open to all' : event.visibility}
                  </b>
                </p>
              </div>
              {canManageEvents ? (
                <button
                  type="button"
                  className="event-edit-link"
                  onClick={(click) => {
                    click.stopPropagation()
                    setEditing(event)
                  }}
                >
                  Edit
                </button>
              ) : null}
              <div>
                <button
                  type="button"
                  className={going ? 'active' : ''}
                  disabled={event.status === 'CANCELLED' || rsvpMutation.isPending}
                  onClick={(click) => {
                    click.stopPropagation()
                    void rsvpMutation.mutateAsync({
                      eventId: event.id,
                      status: going ? 'INTERESTED' : 'GOING',
                    })
                  }}
                >
                  {going ? 'Going' : 'Interested'}
                </button>
                <small>{event.goingCount} going</small>
              </div>
            </article>
          )
        })}
      </div>

      {selected ? (
        <EventDetailModal
          event={selected}
          onClose={closeEvent}
          onRsvp={(status) => void rsvpMutation.mutateAsync({ eventId: selected.id, status })}
          onAddToCalendar={() => {
            void downloadCommunityEventIcs(serverId, selected.id).then((blob) => {
              const url = URL.createObjectURL(blob)
              const anchor = document.createElement('a')
              anchor.href = url
              anchor.download = `${selected.title}.ics`
              anchor.click()
              URL.revokeObjectURL(url)
            })
            navigate(selected.calendarPath)
          }}
          onShare={async () => {
            const url = `${window.location.origin}${selected.sharePath}`
            if (navigator.clipboard?.writeText) {
              await navigator.clipboard.writeText(url)
            }
          }}
          onCancel={
            canManageEvents
              ? () => void cancelMutation.mutateAsync(selected.id)
              : undefined
          }
        />
      ) : null}

      {editing !== undefined ? (
        <CreateEventModal
          event={editing}
          saving={saveMutation.isPending}
          onClose={() => setEditing(undefined)}
          onSave={(input) =>
            void saveMutation.mutateAsync({
              eventId: editing?.id,
              input,
            })
          }
        />
      ) : null}
    </div>
  )
}

function EventDetailModal({
  event,
  onClose,
  onRsvp,
  onAddToCalendar,
  onShare,
  onCancel,
}: {
  event: CommunityEvent
  onClose: () => void
  onRsvp: (status: 'GOING' | 'INTERESTED' | 'NOT_GOING') => void
  onAddToCalendar: () => void
  onShare: () => void
  onCancel?: () => void
}) {
  const when = formatEventWhen(event.startsAt, event.endsAt)
  return (
    <div className="v2-modal-backdrop">
      <section className="event-detail-modal">
        <button type="button" className="modal-close" onClick={onClose}>
          <X />
        </button>
        <header>
          <span>
            <CalendarDays />
          </span>
          <div>
            <h2>{event.title}</h2>
            <p>
              <CalendarDays />
              {when.date}
            </p>
            <p>
              <CalendarDays />
              {when.time}
            </p>
            <p>
              <MapPin />
              {event.location || 'Location TBA'}
            </p>
          </div>
        </header>
        <hr />
        <h3>About this event</h3>
        <p>{event.description || 'No description provided.'}</p>
        <div className="event-tags">
          <b>{event.visibility}</b>
          {event.status === 'CANCELLED' ? <b>CANCELLED</b> : null}
        </div>
        <div className="event-going">
          <UsersRound />
          {event.goingCount} going · {event.interestedCount} interested
          {event.viewerRsvp === 'GOING' ? (
            <b>
              <Check />
              Going
            </b>
          ) : null}
        </div>
        <footer>
          <button type="button" onClick={onAddToCalendar} disabled={event.status === 'CANCELLED'}>
            <CalendarDays />
            Add to calendar
          </button>
          <button type="button" onClick={() => void onShare()}>
            <Share2 />
            Share
          </button>
          {event.status !== 'CANCELLED' ? (
            <button
              type="button"
              className="v2-primary-button"
              onClick={() => onRsvp(event.viewerRsvp === 'GOING' ? 'NOT_GOING' : 'GOING')}
            >
              {event.viewerRsvp === 'GOING' ? 'Not going' : 'Going'}
            </button>
          ) : null}
          {onCancel && event.status !== 'CANCELLED' ? (
            <button type="button" className="v2-outline-button" onClick={onCancel}>
              Cancel event
            </button>
          ) : null}
          <button type="button" className="v2-outline-button" onClick={onClose}>
            Close
          </button>
        </footer>
      </section>
    </div>
  )
}

function CreateEventModal({
  event,
  saving,
  onSave,
  onClose,
}: {
  event: CommunityEvent | null
  saving: boolean
  onSave: (input: CreateCommunityEventInput) => void
  onClose: () => void
}) {
  const defaultStart = event?.startsAt ?? '2026-07-18T14:00:00.000Z'
  const defaultEnd = event?.endsAt ?? '2026-07-18T16:00:00.000Z'
  const [title, setTitle] = useState(event?.title ?? '')
  const [startsAt, setStartsAt] = useState(toLocalInputValue(defaultStart))
  const [endsAt, setEndsAt] = useState(toLocalInputValue(defaultEnd))
  const [place, setPlace] = useState(event?.location ?? '')
  const [description, setDescription] = useState(event?.description ?? '')
  const [hubWide, setHubWide] = useState(event?.visibility !== 'COURSE' && event?.visibility !== 'COHORT')
  const [capacity, setCapacity] = useState(event?.capacity?.toString() ?? '')

  return (
    <div className="v2-modal-backdrop">
      <form
        className="create-event-modal"
        onSubmit={(submit: FormEvent) => {
          submit.preventDefault()
          onSave({
            title: title.trim(),
            description: description.trim() || undefined,
            location: place.trim() || undefined,
            startsAt: fromLocalInputValue(startsAt),
            endsAt: fromLocalInputValue(endsAt),
            capacity: capacity.trim() ? Number(capacity) : undefined,
            visibility: hubWide ? 'HUB' : 'HUB',
          })
        }}
      >
        <button type="button" className="modal-close" onClick={onClose}>
          <X />
        </button>
        <h2>{event ? 'Edit event' : 'Create event'}</h2>
        <label>
          <span>
            <CalendarDays />
          </span>
          <div>
            Event title
            <input value={title} onChange={(change) => setTitle(change.target.value)} required />
          </div>
        </label>
        <div className="create-event-row">
          <label>
            <span>
              <CalendarDays />
            </span>
            <div>
              Starts
              <input
                type="datetime-local"
                value={startsAt}
                onChange={(change) => setStartsAt(change.target.value)}
                required
              />
            </div>
          </label>
          <label>
            <span>
              <CalendarDays />
            </span>
            <div>
              Ends
              <input
                type="datetime-local"
                value={endsAt}
                onChange={(change) => setEndsAt(change.target.value)}
                required
              />
            </div>
          </label>
        </div>
        <div className="create-event-row">
          <label>
            <span>
              <MapPin />
            </span>
            <div>
              Location
              <input value={place} onChange={(change) => setPlace(change.target.value)} />
            </div>
          </label>
          <label>
            <span>
              <UsersRound />
            </span>
            <div>
              Capacity
              <input
                type="number"
                min={1}
                value={capacity}
                onChange={(change) => setCapacity(change.target.value)}
                placeholder="Optional"
              />
            </div>
          </label>
        </div>
        <label>
          <span>
            <CalendarDays />
          </span>
          <div>
            Description
            <textarea value={description} onChange={(change) => setDescription(change.target.value)} />
          </div>
        </label>
        <label className="hub-wide-event">
          <span>
            <UsersRound />
          </span>
          <div>
            <strong>Hub-wide event</strong>
            <small>Visible to everyone in this Study Server.</small>
          </div>
          <button type="button" className={hubWide ? 'active' : ''} onClick={() => setHubWide(!hubWide)}>
            <i />
          </button>
        </label>
        <footer>
          <button type="button" className="v2-outline-button" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="v2-primary-button" disabled={saving}>
            {saving ? 'Saving…' : event ? 'Save changes' : 'Create event'}
          </button>
        </footer>
      </form>
    </div>
  )
}
