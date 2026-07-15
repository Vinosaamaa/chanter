import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'
import { fetchHomeSummary, homeSummaryQueryKey } from './home-summary-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('home-summary-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('fetches home summary from /api/v1/me/home-summary', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      courses: [],
      attention: [],
      upNext: [],
      partialFailures: [],
    })
    await fetchHomeSummary()
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/me/home-summary')
  })

  it('builds a stable query key', () => {
    expect(homeSummaryQueryKey('user-1')).toEqual(['home-summary', 'user-1'])
  })
})
