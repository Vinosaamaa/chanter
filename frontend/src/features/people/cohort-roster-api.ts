import { apiFetch } from '../../lib/api-client'

import type { CohortInvitation, CohortRoster } from './cohort-roster-types'

function cohortPath(cohortId: string): string {
  return `/api/v1/cohorts/${encodeURIComponent(cohortId)}`
}

function userPath(userId: string): string {
  return encodeURIComponent(userId)
}

export function fetchCohortRoster(cohortId: string): Promise<CohortRoster> {
  return apiFetch<CohortRoster>(`${cohortPath(cohortId)}/roster`)
}

export function createCohortInvitation(
  cohortId: string,
  email: string,
): Promise<CohortInvitation> {
  return apiFetch<CohortInvitation>(`${cohortPath(cohortId)}/invitations`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export async function cancelCohortInvitation(
  cohortId: string,
  invitationId: string,
): Promise<void> {
  await apiFetch<void>(
    `${cohortPath(cohortId)}/invitations/${encodeURIComponent(invitationId)}`,
    { method: 'DELETE' },
  )
}

export async function addTeachingAssistant(cohortId: string, userId: string): Promise<void> {
  await apiFetch<void>(`${cohortPath(cohortId)}/teaching-assistants/${userPath(userId)}`, {
    method: 'POST',
  })
}

export async function removeTeachingAssistant(
  cohortId: string,
  userId: string,
): Promise<void> {
  await apiFetch<void>(`${cohortPath(cohortId)}/teaching-assistants/${userPath(userId)}`, {
    method: 'DELETE',
  })
}

export async function assignTeachingAssistant(
  cohortId: string,
  learnerUserIds: string[],
  teachingAssistantUserId: string | null,
): Promise<void> {
  await apiFetch<void>(`${cohortPath(cohortId)}/enrollments/teaching-assistant`, {
    method: 'PATCH',
    body: JSON.stringify({ learnerUserIds, teachingAssistantUserId }),
  })
}

export async function removeCohortEnrollment(
  cohortId: string,
  learnerUserId: string,
): Promise<void> {
  await apiFetch<void>(`${cohortPath(cohortId)}/enrollments/${userPath(learnerUserId)}`, {
    method: 'DELETE',
  })
}
