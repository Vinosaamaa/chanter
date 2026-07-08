import { apiFetch } from '../../lib/api-client'

import type { DirectMessage, DirectMessageListResponse, FriendsListResponse } from './types'

export async function fetchFriends(): Promise<FriendsListResponse> {
  return apiFetch<FriendsListResponse>('/api/v1/friendships')
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
