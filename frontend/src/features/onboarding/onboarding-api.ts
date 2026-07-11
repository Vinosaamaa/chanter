import { apiFetch } from '../../lib/api-client'

import type { CohortEnrollmentRecord, CreatedCourse, CreatedStudyServer } from './onboarding-types'

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
  return apiFetch<CreatedCourse>(`/api/v1/study-servers/${studyServerId}/courses`, {
    method: 'POST',
    body: JSON.stringify({
      title: input.title,
      cohortName: input.cohortName,
    }),
  })
}

export async function enrollLearner(cohortId: string, learnerUserId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/cohorts/${cohortId}/enrollments`, {
    method: 'POST',
    body: JSON.stringify({ learnerUserId }),
  })
}

export async function listCohortEnrollments(cohortId: string): Promise<CohortEnrollmentRecord[]> {
  const response = await apiFetch<{ enrollments: CohortEnrollmentRecord[] }>(
    `/api/v1/cohorts/${cohortId}/enrollments`,
  )
  return response.enrollments
}
