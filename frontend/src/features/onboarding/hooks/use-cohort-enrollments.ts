import { useQuery } from '@tanstack/react-query'

import { listCohortEnrollments } from '../onboarding-api'

export function cohortEnrollmentsQueryKey(cohortId: string | undefined) {
  return ['cohort-enrollments', cohortId] as const
}

export function useCohortEnrollments(cohortId: string | undefined) {
  return useQuery({
    queryKey: cohortEnrollmentsQueryKey(cohortId),
    queryFn: () => listCohortEnrollments(cohortId!),
    enabled: Boolean(cohortId),
  })
}
