import { apiFetch } from '../../lib/api-client'

import type { VoiceMediaToken, VoicePresence } from './voice-types'

type VoicePresenceListResponse = {
  presences: VoicePresence[]
}

export type VoiceChannelScope = 'study' | 'course'

function voiceChannelPath(channelId: string, scope: VoiceChannelScope): string {
  const resource = scope === 'course' ? 'course-channels' : 'study-server-channels'
  return `/api/v1/${resource}/${channelId}`
}

export async function fetchVoiceChannelMediaToken(
  channelId: string,
  scope: VoiceChannelScope = 'study',
): Promise<VoiceMediaToken> {
  return apiFetch<VoiceMediaToken>(`${voiceChannelPath(channelId, scope)}/media-token`, {
    method: 'POST',
  })
}

export async function fetchOfficeHoursMediaToken(sessionId: string): Promise<VoiceMediaToken> {
  return apiFetch<VoiceMediaToken>(`/api/v1/office-hours/${sessionId}/media-token`, {
    method: 'POST',
  })
}

export async function fetchVoicePresences(
  channelId: string,
  scope: VoiceChannelScope = 'study',
): Promise<VoicePresence[]> {
  const response = await apiFetch<VoicePresenceListResponse>(
    `${voiceChannelPath(channelId, scope)}/voice-presences`,
  )
  return response.presences
}

export async function joinVoiceChannel(
  channelId: string,
  scope: VoiceChannelScope = 'study',
): Promise<void> {
  await apiFetch<void>(`${voiceChannelPath(channelId, scope)}/voice-presences`, {
    method: 'POST',
  })
}

export async function leaveVoiceChannel(
  channelId: string,
  scope: VoiceChannelScope = 'study',
): Promise<void> {
  await apiFetch<void>(`${voiceChannelPath(channelId, scope)}/voice-presences`, {
    method: 'DELETE',
  })
}
