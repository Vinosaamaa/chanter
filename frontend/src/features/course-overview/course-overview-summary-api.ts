import { apiFetch } from '../../lib/api-client'
import type { CourseOverviewSummaryResponse } from './course-overview-summary-types'

export function courseOverviewSummaryQueryKey(courseId: string, cohortId: string | undefined) {
  return ['course-overview-summary', courseId, cohortId ?? null] as const
}

export function fetchCourseOverviewSummary(
  courseId: string,
  cohortId?: string,
): Promise<CourseOverviewSummaryResponse> {
  const params = new URLSearchParams()
  if (cohortId) {
    params.set('cohortId', cohortId)
  }
  const query = params.toString()
  return apiFetch<CourseOverviewSummaryResponse>(
    `/api/v1/courses/${encodeURIComponent(courseId)}/overview-summary${query ? `?${query}` : ''}`,
  )
}
