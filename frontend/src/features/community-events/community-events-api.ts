import { apiFetch, apiFetchBlob } from '../../lib/api-client'
import type {
  CommunityEvent,
  CommunityEventFilter,
  CommunityEventListResponse,
  CommunityEventRsvpStatus,
  CreateCommunityEventInput,
} from './community-event-types'

export function communityEventsQueryKey(studyServerId: string, filter: CommunityEventFilter) {
  return ['community-events', studyServerId, filter] as const
}

export function fetchCommunityEvents(
  studyServerId: string,
  filter: CommunityEventFilter = 'UPCOMING',
): Promise<CommunityEventListResponse> {
  const params = new URLSearchParams({ filter })
  return apiFetch<CommunityEventListResponse>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events?${params}`,
  )
}

export function fetchCommunityEvent(
  studyServerId: string,
  eventId: string,
): Promise<CommunityEvent> {
  return apiFetch<CommunityEvent>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events/${encodeURIComponent(eventId)}`,
  )
}

export function createCommunityEvent(
  studyServerId: string,
  input: CreateCommunityEventInput,
): Promise<CommunityEvent> {
  return apiFetch<CommunityEvent>(`/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateCommunityEvent(
  studyServerId: string,
  eventId: string,
  input: CreateCommunityEventInput,
): Promise<CommunityEvent> {
  return apiFetch<CommunityEvent>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events/${encodeURIComponent(eventId)}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export function cancelCommunityEvent(
  studyServerId: string,
  eventId: string,
): Promise<CommunityEvent> {
  return apiFetch<CommunityEvent>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events/${encodeURIComponent(eventId)}/cancel`,
    { method: 'POST' },
  )
}

export function upsertCommunityEventRsvp(
  studyServerId: string,
  eventId: string,
  status: CommunityEventRsvpStatus,
): Promise<CommunityEvent> {
  return apiFetch<CommunityEvent>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events/${encodeURIComponent(eventId)}/rsvp`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    },
  )
}

export function downloadCommunityEventIcs(
  studyServerId: string,
  eventId: string,
): Promise<Blob> {
  return apiFetchBlob(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/events/${encodeURIComponent(eventId)}/ics`,
  )
}
