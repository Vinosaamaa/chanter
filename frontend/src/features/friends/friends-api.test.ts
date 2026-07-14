import { describe, expect, it, vi, beforeEach } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  acceptFriendRequest,
  blockUser,
  cancelFriendRequest,
  declineFriendRequest,
  fetchBlockedUsers,
  fetchCoMembers,
  fetchDirectMessages,
  fetchFriendRequests,
  fetchFriends,
  fetchPublicProfiles,
  sendFriendRequest,
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

  it('loads only co-member discovery candidates', async () => {
    mockedApiFetch.mockResolvedValue({ coMembers: [] })

    await fetchCoMembers()

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/social/co-members')
  })

  it('resolves public profiles without requesting email fields', async () => {
    mockedApiFetch.mockResolvedValue({ profiles: [] })

    await fetchPublicProfiles(['user-a', 'user-b'])

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/auth/profiles/query', {
      method: 'POST',
      body: JSON.stringify({ userIds: ['user-a', 'user-b'] }),
    })
  })

  it('batches public profile lookups at the API limit', async () => {
    const userIds = Array.from({ length: 101 }, (_, index) => `user-${index}`)
    mockedApiFetch
      .mockResolvedValueOnce({
        profiles: userIds.slice(0, 100).map((userId) => ({ userId, displayName: userId })),
      })
      .mockResolvedValueOnce({
        profiles: [{ userId: 'user-100', displayName: 'user-100' }],
      })

    const response = await fetchPublicProfiles(userIds)

    expect(mockedApiFetch).toHaveBeenNthCalledWith(1, '/api/v1/auth/profiles/query', {
      method: 'POST',
      body: JSON.stringify({ userIds: userIds.slice(0, 100) }),
    })
    expect(mockedApiFetch).toHaveBeenNthCalledWith(2, '/api/v1/auth/profiles/query', {
      method: 'POST',
      body: JSON.stringify({ userIds: ['user-100'] }),
    })
    expect(response.profiles).toHaveLength(101)
  })

  it('loads the current users blocked ids', async () => {
    mockedApiFetch.mockResolvedValue({ blockedUserIds: ['user-a'] })

    await fetchBlockedUsers()

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/user-blocks')
  })

  it('sends a friend request to a selected co-member', async () => {
    mockedApiFetch.mockResolvedValue({
      id: 'req-1',
      senderUserId: 'me',
      recipientUserId: 'user-a',
      status: 'PENDING',
      createdAt: '2026-07-05T00:00:00Z',
    })

    await sendFriendRequest('user-a')

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/friend-requests', {
      method: 'POST',
      body: JSON.stringify({ recipientUserId: 'user-a' }),
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
