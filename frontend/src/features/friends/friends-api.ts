import { apiFetch } from '../../lib/api-client'

import type {
  CoMemberListResponse,
  DirectMessage,
  DirectMessageListResponse,
  FriendRequest,
  FriendRequestListResponse,
  FriendsListResponse,
  PublicUserProfileListResponse,
  UserBlockListResponse,
} from './types'

export async function fetchFriends(): Promise<FriendsListResponse> {
  return apiFetch<FriendsListResponse>('/api/v1/friendships')
}

export async function fetchFriendRequests(): Promise<FriendRequestListResponse> {
  return apiFetch<FriendRequestListResponse>('/api/v1/friend-requests')
}

export async function fetchCoMembers(): Promise<CoMemberListResponse> {
  return apiFetch<CoMemberListResponse>('/api/v1/social/co-members')
}

export async function fetchPublicProfiles(
  userIds: string[],
): Promise<PublicUserProfileListResponse> {
  const batches: string[][] = []
  for (let index = 0; index < userIds.length; index += 100) {
    batches.push(userIds.slice(index, index + 100))
  }
  if (batches.length === 0) return { profiles: [] }

  const responses = await Promise.all(
    batches.map((batch) =>
      apiFetch<PublicUserProfileListResponse>('/api/v1/auth/profiles/query', {
        method: 'POST',
        body: JSON.stringify({ userIds: batch }),
      }),
    ),
  )
  return { profiles: responses.flatMap((response) => response.profiles) }
}

export async function fetchBlockedUsers(): Promise<UserBlockListResponse> {
  return apiFetch<UserBlockListResponse>('/api/v1/user-blocks')
}

export async function sendFriendRequest(recipientUserId: string): Promise<FriendRequest> {
  return apiFetch<FriendRequest>('/api/v1/friend-requests', {
    method: 'POST',
    body: JSON.stringify({ recipientUserId }),
  })
}

export async function acceptFriendRequest(friendRequestId: string): Promise<FriendRequest> {
  return apiFetch<FriendRequest>(`/api/v1/friend-requests/${friendRequestId}/acceptance`, {
    method: 'POST',
    body: '{}',
  })
}

export async function declineFriendRequest(friendRequestId: string): Promise<FriendRequest> {
  return apiFetch<FriendRequest>(`/api/v1/friend-requests/${friendRequestId}/decline`, {
    method: 'POST',
    body: '{}',
  })
}

export async function cancelFriendRequest(friendRequestId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/friend-requests/${friendRequestId}/cancellation`, {
    method: 'POST',
    body: '{}',
  })
}

export async function blockUser(blockedUserId: string): Promise<void> {
  await apiFetch<void>('/api/v1/user-blocks', {
    method: 'POST',
    body: JSON.stringify({ blockedUserId }),
  })
}

export async function fetchDirectMessages(peerUserId: string): Promise<DirectMessageListResponse> {
  return apiFetch<DirectMessageListResponse>(
    `/api/v1/direct-messages?peerUserId=${encodeURIComponent(peerUserId)}`,
  )
}

export async function sendDirectMessage(
  recipientUserId: string,
  body: string,
): Promise<DirectMessage> {
  return apiFetch<DirectMessage>('/api/v1/direct-messages', {
    method: 'POST',
    body: JSON.stringify({ recipientUserId, body }),
  })
}
