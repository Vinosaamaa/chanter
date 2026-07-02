import { describe, expect, it, vi, beforeEach } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import {
  fetchOfficeHoursMediaToken,
  fetchVoiceChannelMediaToken,
  fetchVoicePresences,
  leaveVoiceChannel,
} from './voice-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('voice-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('requests a study voice channel media token', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      roomName: 'voice-abc',
      serverUrl: 'ws://localhost:7880',
      participantToken: 'jwt',
      canSpeak: true,
      canListen: true,
    })

    const token = await fetchVoiceChannelMediaToken('channel-1')

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-server-channels/channel-1/media-token', {
      method: 'POST',
    })
    expect(token.participantToken).toBe('jwt')
  })

  it('requests an office hours media token', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      roomName: 'voice-abc',
      serverUrl: 'ws://localhost:7880',
      participantToken: 'jwt',
      canSpeak: true,
      canListen: true,
    })

    await fetchOfficeHoursMediaToken('session-1')

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/office-hours/session-1/media-token', {
      method: 'POST',
    })
  })

  it('loads voice presences and leaves the channel', async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce({ presences: [] })
      .mockResolvedValueOnce(undefined)

    const presences = await fetchVoicePresences('channel-1')
    await leaveVoiceChannel('channel-1')

    expect(presences).toEqual([])
    expect(apiFetch).toHaveBeenLastCalledWith(
      '/api/v1/study-server-channels/channel-1/voice-presences',
      { method: 'DELETE' },
    )
  })
})
