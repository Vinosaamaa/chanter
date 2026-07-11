import { useQuery } from '@tanstack/react-query'

import { listCohortEnrollments } from '../onboarding-api'

export function cohortEnrollmentsQueryKey(
  cohortId: string | undefined,
  options?: { limit?: number; offset?: number },
) {
  return ['cohort-enrollments', cohortId, options?.limit ?? null, options?.offset ?? null] as const
}

export function useCohortEnrollments(
  cohortId: string | undefined,
  options?: { limit?: number; offset?: number },
) {
  return useQuery({
    queryKey: cohortEnrollmentsQueryKey(cohortId, options),
    queryFn: () => listCohortEnrollments(cohortId!, options),
    enabled: Boolean(cohortId),
  })
}
