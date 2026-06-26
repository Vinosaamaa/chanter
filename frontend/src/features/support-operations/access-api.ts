import { apiFetch } from '../../lib/api-client'

import type { CohortOfficeHoursAccess, CohortTaQueueAccess } from './support-operations-types'

export async function fetchCohortTaQueueAccess(cohortId: string): Promise<CohortTaQueueAccess> {
  return apiFetch<CohortTaQueueAccess>(`/api/v1/cohorts/${cohortId}/ta-queue-access`)
}

export async function fetchCohortOfficeHoursAccess(
  cohortId: string,
): Promise<CohortOfficeHoursAccess> {
  return apiFetch<CohortOfficeHoursAccess>(`/api/v1/cohorts/${cohortId}/office-hours-access`)
}
