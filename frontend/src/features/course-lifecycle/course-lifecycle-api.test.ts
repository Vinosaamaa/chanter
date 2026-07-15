import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  acceptStudyServerInvitation,
  addCourseCohort,
  archiveCourse,
  assignCourseInstructor,
  fetchCourseLifecycle,
  fetchPendingStudyServerInvitations,
  publishCourse,
  updateCourseMetadata,
} from './course-lifecycle-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('course-lifecycle-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('fetchCourseLifecycle loads owner governance state', async () => {
    mockedApiFetch.mockResolvedValue({
      id: 'course-1',
      title: 'CS 101',
      description: 'Intro',
      published: false,
      archived: false,
      instructorRole: null,
      cohort: null,
      channels: null,
    })

    const result = await fetchCourseLifecycle('course-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/courses/course-1')
    expect(result.published).toBe(false)
  })

  it('updateCourseMetadata patches title and description', async () => {
    mockedApiFetch.mockResolvedValue({ id: 'course-1', title: 'Updated' })

    await updateCourseMetadata('course-1', { title: 'Updated', description: 'New copy' })

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/courses/course-1', {
      method: 'PATCH',
      body: JSON.stringify({ title: 'Updated', description: 'New copy' }),
    })
  })

  it('addCourseCohort posts cohort name', async () => {
    mockedApiFetch.mockResolvedValue({ id: 'cohort-1', name: 'Spring cohort' })

    const cohort = await addCourseCohort('course-1', 'Spring cohort')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/courses/course-1/cohorts', {
      method: 'POST',
      body: JSON.stringify({ name: 'Spring cohort' }),
    })
    expect(cohort.name).toBe('Spring cohort')
  })

  it('assignCourseInstructor can use instructor email', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await assignCourseInstructor('course-1', { instructorEmail: 'instructor@example.edu' })

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/courses/course-1/instructor', {
      method: 'PATCH',
      body: JSON.stringify({ instructorEmail: 'instructor@example.edu' }),
    })
  })

  it('publishCourse and archiveCourse call lifecycle endpoints', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await publishCourse('course-1')
    await archiveCourse('course-1')

    expect(mockedApiFetch).toHaveBeenNthCalledWith(1, '/api/v1/courses/course-1/publish', {
      method: 'POST',
    })
    expect(mockedApiFetch).toHaveBeenNthCalledWith(2, '/api/v1/courses/course-1/archive', {
      method: 'POST',
    })
  })

  it('lists and accepts durable Study Server invitations', async () => {
    mockedApiFetch.mockResolvedValueOnce([
      {
        id: 'invite-1',
        studyServerId: 'server-1',
        studyServerName: 'Shared Hub',
        email: 'teammate@example.edu',
      },
    ])
    mockedApiFetch.mockResolvedValueOnce(undefined)

    const invites = await fetchPendingStudyServerInvitations()
    await acceptStudyServerInvitation('server-1', 'invite-1')

    expect(invites).toHaveLength(1)
    expect(mockedApiFetch).toHaveBeenLastCalledWith(
      '/api/v1/study-servers/server-1/invitations/invite-1/accept',
      { method: 'POST' },
    )
  })
})
