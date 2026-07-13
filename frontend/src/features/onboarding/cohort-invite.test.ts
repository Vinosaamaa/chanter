import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../lib/api-client'

import {
  completePendingCohortJoin,
  readCohortInviteInput,
  readCohortInviteParams,
  rememberCohortInviteFromSearch,
} from './cohort-invite'

const storage = new Map<string, string>()

describe('cohort-invite', () => {
  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value)
      },
      removeItem: (key: string) => {
        storage.delete(key)
      },
    })
  })

  it('readCohortInviteParams requires cohort and invite query params', () => {
    expect(readCohortInviteParams('?cohort=cohort-1&invite=code-1')).toEqual({
      cohortId: 'cohort-1',
      inviteCode: 'code-1',
    })
    expect(readCohortInviteParams('?cohort=cohort-1')).toBeNull()
    expect(readCohortInviteParams('?invite=code-1')).toBeNull()
  })

  it('reads the full enrollment link copied by an instructor', () => {
    expect(readCohortInviteInput(
      'http://localhost:5173/sign-in?cohort=cohort-1&invite=code-1',
    )).toEqual({ cohortId: 'cohort-1', inviteCode: 'code-1' })
    expect(readCohortInviteInput('not an invite')).toBeNull()
  })

  it('rememberCohortInviteFromSearch stores invite params for post-login join', () => {
    rememberCohortInviteFromSearch('?cohort=cohort-1&invite=code-1')

    expect(storage.get('chanter:pending-cohort-invite')).toBe(
      JSON.stringify({ cohortId: 'cohort-1', inviteCode: 'code-1' }),
    )
  })

  it('completePendingCohortJoin returns none when no pending invite exists', async () => {
    const joinCohort = vi.fn()

    await expect(completePendingCohortJoin(joinCohort)).resolves.toBe('none')
    expect(joinCohort).not.toHaveBeenCalled()
  })

  it('completePendingCohortJoin succeeds and clears pending invite', async () => {
    rememberCohortInviteFromSearch('?cohort=cohort-1&invite=code-1')
    const joinCohort = vi.fn().mockResolvedValue(undefined)

    await expect(completePendingCohortJoin(joinCohort)).resolves.toBe('success')
    expect(joinCohort).toHaveBeenCalledWith('cohort-1', 'code-1')
    expect(storage.has('chanter:pending-cohort-invite')).toBe(false)
  })

  it('completePendingCohortJoin re-queues only transient failures', async () => {
    rememberCohortInviteFromSearch('?cohort=cohort-1&invite=code-1')
    const joinCohort = vi
      .fn()
      .mockRejectedValueOnce(new ApiError('Invalid invite', 403))
      .mockResolvedValue(undefined)

    await expect(completePendingCohortJoin(joinCohort)).resolves.toBe('failed')
    expect(storage.has('chanter:pending-cohort-invite')).toBe(false)

    rememberCohortInviteFromSearch('?cohort=cohort-1&invite=code-1')
    await expect(
      completePendingCohortJoin(
        vi.fn().mockRejectedValueOnce(new ApiError('Server error', 503)),
      ),
    ).resolves.toBe('failed')
    expect(storage.get('chanter:pending-cohort-invite')).toBe(
      JSON.stringify({ cohortId: 'cohort-1', inviteCode: 'code-1' }),
    )
  })
})
