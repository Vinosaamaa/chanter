import { apiFetch } from '../../lib/api-client'

import type { VoiceMediaToken, VoicePresence } from './voice-types'

type VoicePresenceListResponse = {
  presences: VoicePresence[]
}

export async function fetchVoiceChannelMediaToken(channelId: string): Promise<VoiceMediaToken> {
  return apiFetch<VoiceMediaToken>(`/api/v1/study-server-channels/${channelId}/media-token`, {
    method: 'POST',
  })
}

export async function fetchOfficeHoursMediaToken(sessionId: string): Promise<VoiceMediaToken> {
  return apiFetch<VoiceMediaToken>(`/api/v1/office-hours/${sessionId}/media-token`, {
    method: 'POST',
  })
}

export async function fetchVoicePresences(channelId: string): Promise<VoicePresence[]> {
  const response = await apiFetch<VoicePresenceListResponse>(
    `/api/v1/study-server-channels/${channelId}/voice-presences`,
  )
  return response.presences
}

export async function leaveVoiceChannel(channelId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/study-server-channels/${channelId}/voice-presences`, {
    method: 'DELETE',
  })
}
