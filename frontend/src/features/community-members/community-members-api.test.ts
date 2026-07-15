import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'
import {
  createStudyServerInvitations,
  fetchStudyServerMemberSummary,
  fetchStudyServerMembers,
} from './community-members-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('community-members-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('lists members with search and filter', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ members: [], filteredTotal: 0, memberCount: 0 })
    await fetchStudyServerMembers('server-1', { search: 'alex', filter: 'STAFF', limit: 20 })
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/members?search=alex&filter=STAFF&limit=20',
    )
  })

  it('fetches member summary and creates invitations', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ memberCount: 2, preview: [] })
    await fetchStudyServerMemberSummary('server-1')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/member-summary')

    vi.mocked(apiFetch).mockResolvedValue([{ id: 'inv-1', email: 'a@example.edu' }])
    await createStudyServerInvitations('server-1', ['a@example.edu'])
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/invitations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ inviteEmails: ['a@example.edu'] }),
    })
  })
})
