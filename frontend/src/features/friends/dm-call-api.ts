import { apiFetch } from '../../lib/api-client'

import type { VoiceMediaToken } from '../voice/voice-types'

export async function fetchDirectMessageCallMediaToken(callId: string): Promise<VoiceMediaToken> {
  return apiFetch<VoiceMediaToken>(`/api/v1/direct-message-calls/${encodeURIComponent(callId)}/media-token`, {
    method: 'POST',
  })
}
