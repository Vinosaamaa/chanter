import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  archiveCourseChannel,
  createCourseChannel,
  renameCourseChannel,
} from './course-channels-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('course-channels-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('creates, renames, and archives a Cohort channel', async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce({ id: 'channel-1', cohortId: 'cohort-1', name: 'group-a', kind: 'TEXT' })
      .mockResolvedValueOnce({ id: 'channel-1', cohortId: 'cohort-1', name: 'project-room', kind: 'TEXT' })
      .mockResolvedValueOnce(undefined)

    await createCourseChannel('cohort-1', { name: 'Group A', kind: 'TEXT' })
    await renameCourseChannel('channel-1', 'Project Room')
    await archiveCourseChannel('channel-1')

    expect(apiFetch).toHaveBeenNthCalledWith(1, '/api/v1/cohorts/cohort-1/channels', {
      method: 'POST',
      body: JSON.stringify({ name: 'Group A', kind: 'TEXT' }),
    })
    expect(apiFetch).toHaveBeenNthCalledWith(2, '/api/v1/course-channels/channel-1', {
      method: 'PATCH',
      body: JSON.stringify({ name: 'Project Room' }),
    })
    expect(apiFetch).toHaveBeenNthCalledWith(3, '/api/v1/course-channels/channel-1', {
      method: 'DELETE',
    })
  })
})
