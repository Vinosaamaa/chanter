import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  addTeachingAssistant,
  assignTeachingAssistant,
  cancelCohortInvitation,
  createCohortInvitation,
  fetchCohortRoster,
  removeCohortEnrollment,
  removeTeachingAssistant,
} from './cohort-roster-api'

vi.mock('../../lib/api-client', () => ({ apiFetch: vi.fn() }))

const mockedApiFetch = vi.mocked(apiFetch)

describe('cohort-roster-api', () => {
  beforeEach(() => mockedApiFetch.mockReset())

  it('reads the real cohort roster', async () => {
    mockedApiFetch.mockResolvedValue({ learners: [] })

    await fetchCohortRoster('cohort/1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort%2F1/roster')
  })

  it('persists invitations, TA roles, assignments, and removals', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await createCohortInvitation('cohort-1', 'learner@example.edu')
    await cancelCohortInvitation('cohort-1', 'invite-1')
    await addTeachingAssistant('cohort-1', 'user-ta')
    await removeTeachingAssistant('cohort-1', 'user-ta')
    await assignTeachingAssistant('cohort-1', ['learner-1', 'learner-2'], 'user-ta')
    await removeCohortEnrollment('cohort-1', 'learner-1')

    expect(mockedApiFetch).toHaveBeenNthCalledWith(
      1,
      '/api/v1/cohorts/cohort-1/invitations',
      { method: 'POST', body: JSON.stringify({ email: 'learner@example.edu' }) },
    )
    expect(mockedApiFetch).toHaveBeenNthCalledWith(
      2,
      '/api/v1/cohorts/cohort-1/invitations/invite-1',
      { method: 'DELETE' },
    )
    expect(mockedApiFetch).toHaveBeenNthCalledWith(
      3,
      '/api/v1/cohorts/cohort-1/teaching-assistants/user-ta',
      { method: 'POST' },
    )
    expect(mockedApiFetch).toHaveBeenNthCalledWith(
      4,
      '/api/v1/cohorts/cohort-1/teaching-assistants/user-ta',
      { method: 'DELETE' },
    )
    expect(mockedApiFetch).toHaveBeenNthCalledWith(
      5,
      '/api/v1/cohorts/cohort-1/enrollments/teaching-assistant',
      {
        method: 'PATCH',
        body: JSON.stringify({
          learnerUserIds: ['learner-1', 'learner-2'],
          teachingAssistantUserId: 'user-ta',
        }),
      },
    )
    expect(mockedApiFetch).toHaveBeenNthCalledWith(
      6,
      '/api/v1/cohorts/cohort-1/enrollments/learner-1',
      { method: 'DELETE' },
    )
  })
})
