import { apiFetch } from '../../lib/api-client'

export type CourseDiscoveryFilter = 'ALL' | 'ENROLLED' | 'OPEN' | 'OPENING_SOON'
export type CohortEnrollmentPolicy = 'OPEN' | 'INVITE_ONLY' | 'OPENING_SOON' | 'CLOSED'

export type DiscoveredCohort = {
  id: string
  name: string
  enrollmentPolicy: CohortEnrollmentPolicy
  enrolled: boolean
  learnerCount: number
}

export type DiscoveredCourse = {
  id: string
  title: string
  instructorUserId: string
  cohorts: DiscoveredCohort[]
}

export type CourseCatalog = {
  courses: DiscoveredCourse[]
}

export const courseCatalogQueryKey = (
  studyServerId: string,
  search: string,
  filter: CourseDiscoveryFilter,
) => ['course-catalog', studyServerId, search, filter] as const

export function fetchCourseCatalog(
  studyServerId: string,
  options: { search: string; filter: CourseDiscoveryFilter },
): Promise<CourseCatalog> {
  const params = new URLSearchParams({
    search: options.search,
    filter: options.filter,
  })
  return apiFetch<CourseCatalog>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/course-catalog?${params}`,
  )
}

export async function joinDiscoveredCohort(
  cohortId: string,
  inviteCode?: string,
): Promise<void> {
  await apiFetch<void>(`/api/v1/cohorts/${encodeURIComponent(cohortId)}/join`, {
    method: 'POST',
    body: JSON.stringify(inviteCode ? { inviteCode } : {}),
  })
}
