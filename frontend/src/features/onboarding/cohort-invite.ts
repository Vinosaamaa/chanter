import { ApiError } from '../../lib/api-client'

const PENDING_COHORT_INVITE_KEY = 'chanter:pending-cohort-invite'

type PendingCohortInvite = {
  cohortId: string
  inviteCode: string
}

export type CohortJoinResult = 'success' | 'failed' | 'none'

export function readCohortInviteParams(search: string): PendingCohortInvite | null {
  const params = new URLSearchParams(search)
  const cohortId = params.get('cohort')?.trim()
  const inviteCode = params.get('invite')?.trim()
  if (!cohortId || !inviteCode) {
    return null
  }
  return { cohortId, inviteCode }
}

export function storePendingCohortInvite(invite: PendingCohortInvite): void {
  try {
    sessionStorage.setItem(PENDING_COHORT_INVITE_KEY, JSON.stringify(invite))
  } catch {
    // Storage may be blocked; invite join is best-effort.
  }
}

function consumePendingCohortInvite(): PendingCohortInvite | null {
  try {
    const raw = sessionStorage.getItem(PENDING_COHORT_INVITE_KEY)
    if (raw) {
      sessionStorage.removeItem(PENDING_COHORT_INVITE_KEY)
      return JSON.parse(raw) as PendingCohortInvite
    }
    return null
  } catch {
    return null
  }
}

function isTransientJoinError(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return true
  }
  return error.status >= 500
}

export async function completePendingCohortJoin(
  joinCohort: (cohortId: string, inviteCode: string) => Promise<void>,
): Promise<CohortJoinResult> {
  const pending = consumePendingCohortInvite()
  if (!pending) {
    return 'none'
  }

  try {
    await joinCohort(pending.cohortId, pending.inviteCode)
    return 'success'
  } catch (error) {
    if (isTransientJoinError(error)) {
      storePendingCohortInvite(pending)
    }
    return 'failed'
  }
}

export function rememberCohortInviteFromSearch(search: string): void {
  const invite = readCohortInviteParams(search)
  if (invite) {
    storePendingCohortInvite(invite)
  }
}
