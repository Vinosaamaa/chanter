import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import { reindexStudyServer, searchStudyServer } from './global-search-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('global-search-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('searchStudyServer passes the query string', async () => {
    mockedApiFetch.mockResolvedValue({ results: [] })

    await searchStudyServer('server-1', 'homework')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/search?q=homework')
  })

  it('reindexStudyServer posts to the reindex endpoint', async () => {
    mockedApiFetch.mockResolvedValue({ indexedDocuments: 3 })

    const result = await reindexStudyServer('server-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/search/reindex', {
      method: 'POST',
    })
    expect(result.indexedDocuments).toBe(3)
  })
})
