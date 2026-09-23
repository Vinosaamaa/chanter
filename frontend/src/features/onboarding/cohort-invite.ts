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

export function readCohortInviteInput(input: string): PendingCohortInvite | null {
  try {
    const url = new URL(input.trim(), window.location.origin)
    return readCohortInviteParams(url.search)
  } catch {
    return null
  }
}

export function storePendingCohortInvite(invite: PendingCohortInvite): void {
  try {
    sessionStorage.setItem(PENDING_COHORT_INVITE_KEY, JSON.stringify(invite))
  } catch {
    // Storage may be blocked; invite join is best-effort.
  }
}

function readPendingCohortInvite(): PendingCohortInvite | null {
  try {
    const raw = sessionStorage.getItem(PENDING_COHORT_INVITE_KEY)
    if (!raw) return null
    try {
      const parsed: unknown = JSON.parse(raw)
      if (parsed && typeof parsed === 'object' && 'cohortId' in parsed && 'inviteCode' in parsed
        && typeof parsed.cohortId === 'string' && parsed.cohortId.trim()
        && typeof parsed.inviteCode === 'string' && parsed.inviteCode.trim()) {
        return { cohortId: parsed.cohortId.trim(), inviteCode: parsed.inviteCode.trim() }
      }
    } catch {
      // A corrupt saved value must not keep intercepting future sign-ins.
    }
    sessionStorage.removeItem(PENDING_COHORT_INVITE_KEY)
    return null
  } catch {
    return null
  }
}

function clearPendingCohortInvite(expected: PendingCohortInvite): void {
  try {
    const current = readPendingCohortInvite()
    if (current?.cohortId === expected.cohortId && current.inviteCode === expected.inviteCode) {
      sessionStorage.removeItem(PENDING_COHORT_INVITE_KEY)
    }
  } catch {
    // A later explicit visit can retry the idempotent join if storage is unavailable.
  }
}

function isTransientJoinError(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return true
  }
  return error.status >= 500 || [401, 408, 409, 429].includes(error.status)
}

export async function completePendingCohortJoin(
  joinCohort: (cohortId: string, inviteCode: string) => Promise<void>,
): Promise<CohortJoinResult> {
  const pending = readPendingCohortInvite()
  if (!pending) {
    return 'none'
  }

  try {
    await joinCohort(pending.cohortId, pending.inviteCode)
    clearPendingCohortInvite(pending)
    return 'success'
  } catch (error) {
    if (!isTransientJoinError(error)) clearPendingCohortInvite(pending)
    return 'failed'
  }
}

export function rememberCohortInviteFromSearch(search: string): void {
  const invite = readCohortInviteParams(search)
  if (invite) {
    storePendingCohortInvite(invite)
  }
}
