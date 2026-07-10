import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import { deleteStudyServer, fetchAccessibleStudyServers } from './shell-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('shell-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('fetchAccessibleStudyServers loads server summaries', async () => {
    mockedApiFetch.mockResolvedValue([
      {
        id: 'server-1',
        name: 'Bootcamp Hub',
        owner: true,
        courseCount: 2,
        memberCount: 5,
      },
    ])

    const result = await fetchAccessibleStudyServers()

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/study-servers')
    expect(result[0]?.owner).toBe(true)
    expect(result[0]?.courseCount).toBe(2)
  })

  it('deleteStudyServer sends DELETE for the server id', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await deleteStudyServer('server-9')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-9', {
      method: 'DELETE',
    })
  })
})
