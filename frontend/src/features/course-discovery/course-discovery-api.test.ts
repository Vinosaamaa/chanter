import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import { fetchCourseCatalog, joinDiscoveredCohort } from './course-discovery-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('course-discovery-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('queries the server catalog and joins open or invite-only cohorts', async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce({ courses: [] })
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(undefined)

    await fetchCourseCatalog('server-1', { search: 'linear algebra', filter: 'OPEN' })
    await joinDiscoveredCohort('cohort-open')
    await joinDiscoveredCohort('cohort-invite', 'invite-code')

    expect(apiFetch).toHaveBeenNthCalledWith(
      1,
      '/api/v1/study-servers/server-1/course-catalog?search=linear+algebra&filter=OPEN',
    )
    expect(apiFetch).toHaveBeenNthCalledWith(2, '/api/v1/cohorts/cohort-open/join', {
      method: 'POST',
      body: JSON.stringify({}),
    })
    expect(apiFetch).toHaveBeenNthCalledWith(3, '/api/v1/cohorts/cohort-invite/join', {
      method: 'POST',
      body: JSON.stringify({ inviteCode: 'invite-code' }),
    })
  })
})
