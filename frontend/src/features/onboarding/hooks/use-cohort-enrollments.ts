import { useQuery } from '@tanstack/react-query'

import { getCohortInvite, listCohortEnrollments } from '../onboarding-api'

export function cohortEnrollmentsQueryKey(
  cohortId: string | undefined,
  options?: { limit?: number; offset?: number; search?: string },
) {
  return [
    'cohort-enrollments',
    cohortId,
    options?.limit ?? null,
    options?.offset ?? null,
    options?.search ?? null,
  ] as const
}

export function useCohortEnrollments(
  cohortId: string | undefined,
  options?: { limit?: number; offset?: number; search?: string },
) {
  return useQuery({
    queryKey: cohortEnrollmentsQueryKey(cohortId, options),
    queryFn: () => listCohortEnrollments(cohortId!, options),
    enabled: Boolean(cohortId),
  })
}

export function useCohortInvite(cohortId: string | undefined) {
  return useQuery({
    queryKey: ['cohort-invite', cohortId],
    queryFn: () => getCohortInvite(cohortId!),
    enabled: Boolean(cohortId),
  })
}
