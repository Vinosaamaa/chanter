import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  fetchStudyAssistantInstallPreview,
  installStudyAssistant,
} from './study-assistant-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('study-assistant-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('loads install preview for an instructor', async () => {
    mockedApiFetch.mockResolvedValue({
      studyServerId: 'server-1',
      alreadyInstalled: false,
      candidates: { studyServerId: 'server-1', studyServerChannels: [], courses: [] },
      courseResources: [],
    })

    const preview = await fetchStudyAssistantInstallPreview('server-1', 'instructor-1')

    expect(mockedApiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/study-assistant/install-preview?instructorUserId=instructor-1',
    )
    expect(preview.alreadyInstalled).toBe(false)
  })

  it('confirms install with selected grants', async () => {
    mockedApiFetch.mockResolvedValue({
      studyServerId: 'server-1',
      installed: true,
      grants: [{ grantType: 'COURSE_CHANNEL', grantTargetId: 'channel-1' }],
    })

    const presence = await installStudyAssistant('server-1', 'instructor-1', [
      { grantType: 'COURSE_CHANNEL', grantTargetId: 'channel-1' },
    ])

    expect(mockedApiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/study-assistant/install',
      {
        method: 'POST',
        body: JSON.stringify({
          instructorUserId: 'instructor-1',
          grants: [{ grantType: 'COURSE_CHANNEL', grantTargetId: 'channel-1' }],
        }),
      },
    )
    expect(presence.installed).toBe(true)
  })
})
