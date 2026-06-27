import { apiFetch } from '../../lib/api-client'

import type {
  InstructorDashboard,
  SaasPlan,
  SaasPlanTier,
  StudyServerDetails,
} from './instructor-dashboard-types'

export async function fetchInstructorDashboard(
  studyServerId: string,
  viewerUserId: string,
): Promise<InstructorDashboard> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<InstructorDashboard>(
    `/api/v1/study-servers/${studyServerId}/instructor-dashboard?${params.toString()}`,
  )
}

export async function fetchSaasPlan(studyServerId: string): Promise<SaasPlan> {
  return apiFetch<SaasPlan>(`/api/v1/study-servers/${studyServerId}/saas-plan`)
}

export async function updateSaasPlan(
  studyServerId: string,
  planTier: SaasPlanTier,
): Promise<SaasPlan> {
  return apiFetch<SaasPlan>(`/api/v1/study-servers/${studyServerId}/saas-plan`, {
    method: 'PATCH',
    body: JSON.stringify({ planTier }),
  })
}

export async function fetchStudyServerDetails(studyServerId: string): Promise<StudyServerDetails> {
  return apiFetch<StudyServerDetails>(`/api/v1/study-servers/${studyServerId}`)
}
