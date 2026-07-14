import { apiFetch } from '../../lib/api-client'

import type {
  OfficeHoursParticipant,
  OfficeHoursParticipantListResponse,
  OfficeHoursSession,
  OfficeHoursSessionListResponse,
} from './support-operations-types'

export type OfficeHoursScheduleInput = {
  startsAt: string
  endsAt: string
}

export async function scheduleOfficeHours(
  cohortId: string,
  input: OfficeHoursScheduleInput,
): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/cohorts/${cohortId}/office-hours`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function listOfficeHoursSessions(
  cohortId: string,
): Promise<OfficeHoursSessionListResponse> {
  return apiFetch<OfficeHoursSessionListResponse>(`/api/v1/cohorts/${cohortId}/office-hours`)
}

export async function updateOfficeHoursSession(
  sessionId: string,
  input: OfficeHoursScheduleInput,
): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/office-hours/${sessionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export async function cancelOfficeHoursSession(sessionId: string): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/office-hours/${sessionId}`, {
    method: 'DELETE',
  })
}

export async function startOfficeHoursSession(sessionId: string): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/office-hours/${sessionId}/start`, {
    method: 'POST',
  })
}

export async function endOfficeHoursSession(sessionId: string): Promise<OfficeHoursSession> {
  return apiFetch<OfficeHoursSession>(`/api/v1/office-hours/${sessionId}/end`, {
    method: 'POST',
  })
}

export async function joinOfficeHoursSession(sessionId: string): Promise<OfficeHoursParticipant> {
  return apiFetch<OfficeHoursParticipant>(`/api/v1/office-hours/${sessionId}/participants`, {
    method: 'POST',
  })
}

export async function listOfficeHoursParticipants(
  sessionId: string,
): Promise<OfficeHoursParticipantListResponse> {
  return apiFetch<OfficeHoursParticipantListResponse>(
    `/api/v1/office-hours/${sessionId}/participants`,
  )
}

export async function updateOfficeHoursHand(
  sessionId: string,
  raised: boolean,
): Promise<OfficeHoursParticipant> {
  return apiFetch<OfficeHoursParticipant>(
    `/api/v1/office-hours/${sessionId}/participants/me/hand`,
    { method: 'PATCH', body: JSON.stringify({ raised }) },
  )
}

export async function updateOfficeHoursSpeaking(
  sessionId: string,
  userId: string,
  canSpeak: boolean,
): Promise<OfficeHoursParticipant> {
  return apiFetch<OfficeHoursParticipant>(
    `/api/v1/office-hours/${sessionId}/participants/${userId}/speaking`,
    { method: 'PATCH', body: JSON.stringify({ canSpeak }) },
  )
}

export async function leaveOfficeHoursSession(sessionId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/office-hours/${sessionId}/participants/me`, {
    method: 'DELETE',
  })
}
