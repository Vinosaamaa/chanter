import { apiFetch } from '../../lib/api-client'

import type {
  DirectMessage,
  DirectMessageListResponse,
  FriendRequest,
  FriendRequestListResponse,
  FriendsListResponse,
} from './types'

export async function fetchFriends(): Promise<FriendsListResponse> {
  return apiFetch<FriendsListResponse>('/api/v1/friendships')
}

export async function fetchFriendRequests(): Promise<FriendRequestListResponse> {
  return apiFetch<FriendRequestListResponse>('/api/v1/friend-requests')
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
