import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { useTaQueuePanel } from './use-ta-queue-panel'

const mocks = vi.hoisted(() => ({
  fetchAccess: vi.fn(),
  listItems: vi.fn(),
}))

vi.mock('../../friends/friends-api', () => ({
  fetchPublicProfiles: vi.fn().mockResolvedValue({ profiles: [] }),
}))

vi.mock('../access-api', () => ({
  fetchCohortTaQueueAccess: mocks.fetchAccess,
}))

vi.mock('../ta-queue-api', () => ({
  cancelTaQueueItem: vi.fn(),
  listTaQueueItems: mocks.listItems,
  pickupTaQueueItem: vi.fn(),
  resolveTaQueueItem: vi.fn(),
}))

describe('useTaQueuePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: {
        id: 'instructor-1',
        email: 'instructor@example.com',
        displayName: 'Instructor Ada',
      },
    })
  })

  it('hides the previous cohort queue while the next cohort is loading', async () => {
    let resolveSecondAccess: ((value: { canManageTaQueue: boolean }) => void) | undefined
    mocks.fetchAccess.mockImplementation((cohortId: string) => {
      if (cohortId === 'cohort-1') return Promise.resolve({ canManageTaQueue: true })
      return new Promise((resolve) => {
        resolveSecondAccess = resolve
      })
    })
    mocks.listItems.mockResolvedValue({
      taQueueItems: [{
        id: 'queue-1',
        cohortId: 'cohort-1',
        supportQuestionId: 'question-1',
        channelId: 'questions-1',
        learnerUserId: 'learner-1',
        body: 'Help with recursion',
        status: 'OPEN',
        assignedTaUserId: null,
        createdAt: '2026-07-14T20:00:00.000Z',
        updatedAt: '2026-07-14T20:00:00.000Z',
      }],
    })

    const { result, rerender } = renderHook(
      ({ cohortId }) => useTaQueuePanel(cohortId),
      { initialProps: { cohortId: 'cohort-1' } },
    )
    await waitFor(() => expect(result.current.items).toHaveLength(1))

    rerender({ cohortId: 'cohort-2' })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.items).toEqual([])
    expect(result.current.canManage).toBe(false)

    resolveSecondAccess?.({ canManageTaQueue: false })
  })
})
