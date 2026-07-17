import { apiFetch } from '../../lib/api-client'

import {
  type ChannelMessageAccess,
  type ChannelMessageListResponse,
  type ChannelScope,
  channelMessageAccessPath,
  messagesPath,
} from './channel-message-types'

export async function fetchChannelMessages(
  scope: ChannelScope,
  channelId: string,
  since?: string,
  afterMessageId?: string,
): Promise<ChannelMessageListResponse> {
  const params = new URLSearchParams()
  if (since) {
    params.set('since', since)
  }
  if (afterMessageId) {
    params.set('afterMessageId', afterMessageId)
  }
  const query = params.size > 0 ? `?${params.toString()}` : ''
  return apiFetch<ChannelMessageListResponse>(`${messagesPath(scope, channelId)}${query}`)
}

export async function fetchChannelMessageAccess(
  scope: ChannelScope,
  channelId: string,
): Promise<ChannelMessageAccess> {
  return apiFetch<ChannelMessageAccess>(channelMessageAccessPath(scope, channelId))
}

export async function postChannelMessage(
  scope: ChannelScope,
  channelId: string,
  body: string,
): Promise<void> {
  await apiFetch(`${messagesPath(scope, channelId)}`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  })
}
