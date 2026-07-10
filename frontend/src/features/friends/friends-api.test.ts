import { describe, expect, it, vi, beforeEach } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  acceptFriendRequest,
  blockUser,
  cancelFriendRequest,
  declineFriendRequest,
  fetchDirectMessages,
  fetchFriendRequests,
  fetchFriends,
  sendDirectMessage,
} from './friends-api'

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

  it('loads pending friend requests for the inbox', async () => {
    mockedApiFetch.mockResolvedValue({
      incoming: [
        {
          id: 'req-1',
          senderUserId: 'user-a',
          recipientUserId: 'me',
          status: 'PENDING',
          createdAt: '2026-07-05T00:00:00Z',
        },
      ],
      outgoing: [],
    })

    const response = await fetchFriendRequests()

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/friend-requests')
    expect(response.incoming).toHaveLength(1)
  })

  it('accepts a friend request', async () => {
    mockedApiFetch.mockResolvedValue({
      id: 'req-1',
      senderUserId: 'user-a',
      recipientUserId: 'me',
      status: 'ACCEPTED',
      createdAt: '2026-07-05T00:00:00Z',
    })

    await acceptFriendRequest('req-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/friend-requests/req-1/acceptance', {
      method: 'POST',
      body: '{}',
    })
  })

  it('declines a friend request', async () => {
    mockedApiFetch.mockResolvedValue({
      id: 'req-1',
      senderUserId: 'user-a',
      recipientUserId: 'me',
      status: 'DECLINED',
      createdAt: '2026-07-05T00:00:00Z',
    })

    await declineFriendRequest('req-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/friend-requests/req-1/decline', {
      method: 'POST',
      body: '{}',
    })
  })

  it('cancels an outgoing friend request', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await cancelFriendRequest('req-1')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/friend-requests/req-1/cancellation', {
      method: 'POST',
      body: '{}',
    })
  })

  it('blocks a user from the inbox', async () => {
    mockedApiFetch.mockResolvedValue(undefined)

    await blockUser('user-a')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/user-blocks', {
      method: 'POST',
      body: JSON.stringify({ blockedUserId: 'user-a' }),
    })
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
