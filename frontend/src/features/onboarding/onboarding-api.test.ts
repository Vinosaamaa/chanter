import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  createCourse,
  createStudyServer,
  enrollLearner,
  joinCohort,
  listCohortEnrollments,
} from './onboarding-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('onboarding-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('createStudyServer posts the server name', async () => {
    mockedApiFetch.mockResolvedValue({ id: 'server-1', name: 'Bootcamp Hub' })

    const result = await createStudyServer('Bootcamp Hub')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/study-servers', {
      method: 'POST',
      body: JSON.stringify({ name: 'Bootcamp Hub' }),
    })
    expect(result).toEqual({ id: 'server-1', name: 'Bootcamp Hub' })
  })

  it('createCourse posts title and cohort name for a study server', async () => {
    mockedApiFetch.mockResolvedValue({
      id: 'course-1',
      title: 'Spring Boot',
      cohort: { id: 'cohort-1', name: 'Summer 2026' },
      channels: [],
    })

    const result = await createCourse('server-1', {
      title: 'Spring Boot',
      cohortName: 'Summer 2026',
    })

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/courses', {
      method: 'POST',
      body: JSON.stringify({ title: 'Spring Boot', cohortName: 'Summer 2026' }),
    })
    expect(result.id).toBe('course-1')
  })

  it('enrollLearner posts the learner registered account email', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await enrollLearner('cohort-1', 'learner@example.edu')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort-1/enrollments', {
      method: 'POST',
      body: JSON.stringify({ email: 'learner@example.edu' }),
    })
  })

  it('joinCohort posts invite code to the learner self-enroll endpoint', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await joinCohort('cohort-1', 'invite-code-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort-1/join', {
      method: 'POST',
      body: JSON.stringify({ inviteCode: 'invite-code-1' }),
    })
  })

  it('joinCohort encodes cohort ids in the request path', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await joinCohort('cohort/with/slash', 'invite-code-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort%2Fwith%2Fslash/join', {
      method: 'POST',
      body: JSON.stringify({ inviteCode: 'invite-code-1' }),
    })
  })

  it('listCohortEnrollments fetches enrollments for a cohort', async () => {
    mockedApiFetch.mockResolvedValue({
      enrollments: [
        {
          learnerUserId: 'learner-9',
          enrolledByUserId: 'instructor-1',
          enrolledAt: '2026-06-01T12:00:00Z',
        },
      ],
      totalCount: 1,
      limit: 50,
      offset: 0,
    })

    const result = await listCohortEnrollments('cohort-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort-1/enrollments')
    expect(result.enrollments).toEqual([
      {
        learnerUserId: 'learner-9',
        enrolledByUserId: 'instructor-1',
        enrolledAt: '2026-06-01T12:00:00Z',
      },
    ])
    expect(result.totalCount).toBe(1)
  })

  it('listCohortEnrollments passes limit and offset query params', async () => {
    mockedApiFetch.mockResolvedValue({
      enrollments: [],
      totalCount: 0,
      limit: 8,
      offset: 16,
    })

    await listCohortEnrollments('cohort-1', { limit: 8, offset: 16, search: 'learner' })

    expect(mockedApiFetch).toHaveBeenCalledWith(
      '/api/v1/cohorts/cohort-1/enrollments?limit=8&offset=16&search=learner',
    )
  })
})
