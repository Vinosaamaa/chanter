import { describe, expect, it, vi, beforeEach } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import { fetchDirectMessages, fetchFriends, sendDirectMessage } from './friends-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('friends-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('loads the authenticated friends list', async () => {
    mockedApiFetch.mockResolvedValue({
      friends: [{ friendUserId: 'friend-1', friendsSince: '2026-07-05T00:00:00Z' }],
    })

    const response = await fetchFriends()

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/friendships')
    expect(response.friends).toHaveLength(1)
  })

  it('loads direct messages for a selected friend', async () => {
    mockedApiFetch.mockResolvedValue({ messages: [] })

    await fetchDirectMessages('peer-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/direct-messages?peerUserId=peer-1')
  })

  it('sends a direct message through message-service', async () => {
    mockedApiFetch.mockResolvedValue({
      id: 'dm-1',
      senderUserId: 'me',
      recipientUserId: 'peer-1',
      body: 'Hello',
      sentAt: '2026-07-05T00:00:00Z',
    })

    await sendDirectMessage('peer-1', 'Hello')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/direct-messages', {
      method: 'POST',
      body: JSON.stringify({ recipientUserId: 'peer-1', body: 'Hello' }),
    })
  })
})
