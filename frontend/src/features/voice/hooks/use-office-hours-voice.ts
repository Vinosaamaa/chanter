import { useCallback, useState } from 'react'

import { fetchOfficeHoursMediaToken, leaveVoiceChannel } from '../voice-api'
import type { VoiceConnectionStatus } from '../voice-types'

import { useLiveKitRoom } from './use-livekit-room'

type UseOfficeHoursVoiceResult = {
  status: VoiceConnectionStatus
  error: string | null
  isMuted: boolean
  isBusy: boolean
  joinVoice: () => Promise<void>
  leaveVoice: () => Promise<void>
  toggleMute: () => Promise<void>
}

export function useOfficeHoursVoice(
  sessionId: string | null,
  voiceChannelId: string | null,
): UseOfficeHoursVoiceResult {
  const liveKit = useLiveKitRoom()
  const [isBusy, setIsBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const joinVoice = useCallback(async () => {
    if (!sessionId) {
      return
    }
    setIsBusy(true)
    setActionError(null)
    try {
      const token = await fetchOfficeHoursMediaToken(sessionId)
      await liveKit.connect(token)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Unable to join Office Hours voice.')
    } finally {
      setIsBusy(false)
    }
  }, [liveKit, sessionId])

  const leaveVoice = useCallback(async () => {
    if (!voiceChannelId) {
      await liveKit.disconnect()
      return
    }
    setIsBusy(true)
    setActionError(null)
    try {
      await liveKit.disconnect()
      await leaveVoiceChannel(voiceChannelId)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Unable to leave Office Hours voice.')
    } finally {
      setIsBusy(false)
    }
  }, [liveKit, voiceChannelId])

  return {
    status: liveKit.status,
    error: actionError ?? liveKit.error,
    isMuted: liveKit.isMuted,
    isBusy,
    joinVoice,
    leaveVoice,
    toggleMute: liveKit.toggleMute,
  }
}
