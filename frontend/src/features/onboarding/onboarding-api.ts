import { apiFetch } from '../../lib/api-client'

import type { CohortEnrollmentListResult, CreatedCourse, CreatedStudyServer } from './onboarding-types'

function cohortPath(cohortId: string): string {
  return `/api/v1/cohorts/${encodeURIComponent(cohortId)}`
}

export async function createStudyServer(name: string): Promise<CreatedStudyServer> {
  return apiFetch<CreatedStudyServer>('/api/v1/study-servers', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export async function createCourse(
  studyServerId: string,
  input: { title: string; cohortName: string },
): Promise<CreatedCourse> {
  return apiFetch<CreatedCourse>(`/api/v1/study-servers/${encodeURIComponent(studyServerId)}/courses`, {
    method: 'POST',
    body: JSON.stringify({
      title: input.title,
      cohortName: input.cohortName,
    }),
  })
}

export async function enrollLearner(cohortId: string, learnerUserId: string): Promise<void> {
  await apiFetch<void>(`${cohortPath(cohortId)}/enrollments`, {
    method: 'POST',
    body: JSON.stringify({ learnerUserId }),
  })
}

export async function joinCohort(cohortId: string, inviteCode: string): Promise<void> {
  await apiFetch<void>(`${cohortPath(cohortId)}/join`, {
    method: 'POST',
    body: JSON.stringify({ inviteCode }),
  })
}

export async function getCohortInvite(cohortId: string): Promise<{ cohortId: string; inviteCode: string }> {
  return apiFetch<{ cohortId: string; inviteCode: string }>(`${cohortPath(cohortId)}/invite`)
}

export async function listCohortEnrollments(
  cohortId: string,
  options?: { limit?: number; offset?: number; search?: string },
): Promise<CohortEnrollmentListResult> {
  const params = new URLSearchParams()
  if (options?.limit !== undefined) {
    params.set('limit', String(options.limit))
  }
  if (options?.offset !== undefined) {
    params.set('offset', String(options.offset))
  }
  if (options?.search) {
    params.set('search', options.search)
  }
  const query = params.toString()
  const path = `${cohortPath(cohortId)}/enrollments${query ? `?${query}` : ''}`
  return apiFetch<CohortEnrollmentListResult>(path)
}
