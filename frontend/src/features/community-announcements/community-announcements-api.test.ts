import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'
import {
  archiveCommunityAnnouncement,
  createCommunityAnnouncement,
  fetchCommunityAnnouncements,
  upsertCommunityAnnouncementLike,
} from './community-announcements-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('community-announcements-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('lists announcements with status query', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ announcements: [] })
    await fetchCommunityAnnouncements('server-1', 'ARCHIVED')
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/announcements?status=ARCHIVED',
    )
  })

  it('creates announcements and upserts likes', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'a1' })
    await createCommunityAnnouncement('server-1', { title: 'Hello', body: 'World' })
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/announcements', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Hello', body: 'World' }),
    })
    await upsertCommunityAnnouncementLike('server-1', 'a1', true)
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/announcements/a1/reactions',
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ liked: true }),
      },
    )
    await archiveCommunityAnnouncement('server-1', 'a1')
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/announcements/a1/archive',
      { method: 'POST' },
    )
  })
})
