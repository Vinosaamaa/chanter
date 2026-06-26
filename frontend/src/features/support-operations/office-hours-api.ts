import { apiFetch } from '../../lib/api-client'

import type {
  OfficeHoursSession,
  OfficeHoursSessionListResponse,
  OfficeHoursWaitlistEntry,
  OfficeHoursWaitlistListResponse,
} from './support-operations-types'

export async function scheduleOfficeHours(
  cohortId: string,
  instructorUserId: string,
  startsAt: string,
  endsAt: string,
): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/cohorts/${cohortId}/office-hours`, {
    method: 'POST',
    body: JSON.stringify({ instructorUserId, startsAt, endsAt }),
  })
}

export async function listOfficeHoursSessions(
  cohortId: string,
  viewerUserId: string,
): Promise<OfficeHoursSessionListResponse> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<OfficeHoursSessionListResponse>(
    `/api/v1/cohorts/${cohortId}/office-hours?${params.toString()}`,
  )
}

export async function joinOfficeHoursWaitlist(
  sessionId: string,
  learnerUserId: string,
): Promise<OfficeHoursWaitlistEntry> {
  return apiFetch<OfficeHoursWaitlistEntry>(`/api/v1/office-hours/${sessionId}/waitlist`, {
    method: 'POST',
    body: JSON.stringify({ learnerUserId }),
  })
}

export async function listOfficeHoursWaitlist(
  sessionId: string,
  viewerUserId: string,
): Promise<OfficeHoursWaitlistListResponse> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<OfficeHoursWaitlistListResponse>(
    `/api/v1/office-hours/${sessionId}/waitlist?${params.toString()}`,
  )
}

export async function admitNextOfficeHoursLearner(
  sessionId: string,
  actorUserId: string,
): Promise<OfficeHoursWaitlistEntry> {
  return apiFetch<OfficeHoursWaitlistEntry>(`/api/v1/office-hours/${sessionId}/admit-next`, {
    method: 'POST',
    body: JSON.stringify({ actorUserId }),
  })
}

export async function endOfficeHoursSession(
  sessionId: string,
  actorUserId: string,
): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/office-hours/${sessionId}/end`, {
    method: 'POST',
    body: JSON.stringify({ actorUserId }),
  })
}
