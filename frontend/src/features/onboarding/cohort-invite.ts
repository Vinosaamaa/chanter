const PENDING_COHORT_INVITE_KEY = 'chanter:pending-cohort-invite'

export function readCohortInviteParam(search: string): string | null {
  const cohortId = new URLSearchParams(search).get('cohort')?.trim()
  return cohortId || null
}

export function storePendingCohortInvite(cohortId: string): void {
  try {
    sessionStorage.setItem(PENDING_COHORT_INVITE_KEY, cohortId)
  } catch {
    // Storage may be blocked; invite join is best-effort.
  }
}

function consumePendingCohortInvite(): string | null {
  try {
    const cohortId = sessionStorage.getItem(PENDING_COHORT_INVITE_KEY)
    if (cohortId) {
      sessionStorage.removeItem(PENDING_COHORT_INVITE_KEY)
    }
    return cohortId
  } catch {
    return null
  }
}

export async function completePendingCohortJoin(
  joinCohort: (cohortId: string) => Promise<void>,
): Promise<void> {
  const cohortId = consumePendingCohortInvite()
  if (!cohortId) {
    return
  }

  try {
    await joinCohort(cohortId)
  } catch {
    storePendingCohortInvite(cohortId)
  }
}

export function rememberCohortInviteFromSearch(search: string): void {
  const cohortId = readCohortInviteParam(search)
  if (cohortId) {
    storePendingCohortInvite(cohortId)
  }
}
