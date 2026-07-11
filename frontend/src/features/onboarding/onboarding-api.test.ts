import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  createCourse,
  createStudyServer,
  enrollLearner,
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

  it('enrollLearner posts the learner user id', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await enrollLearner('cohort-1', 'learner-9')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort-1/enrollments', {
      method: 'POST',
      body: JSON.stringify({ learnerUserId: 'learner-9' }),
    })
  })

  it('listCohortEnrollments fetches enrollments for a cohort', async () => {
    mockedApiFetch.mockResolvedValue({
      enrollments: [
        { learnerUserId: 'learner-9', enrolledAt: '2026-06-01T12:00:00Z' },
      ],
    })

    const result = await listCohortEnrollments('cohort-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/cohorts/cohort-1/enrollments')
    expect(result).toEqual([
      { learnerUserId: 'learner-9', enrolledAt: '2026-06-01T12:00:00Z' },
    ])
  })
})
