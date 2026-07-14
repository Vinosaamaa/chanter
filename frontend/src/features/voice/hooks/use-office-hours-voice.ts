import { useCallback, useState } from 'react'

import { fetchOfficeHoursMediaToken } from '../voice-api'
import type { VoiceConnectionStatus } from '../voice-types'

import { useLiveKitRoom } from './use-livekit-room'

type UseOfficeHoursVoiceResult = {
  status: VoiceConnectionStatus
  error: string | null
  isMuted: boolean
  canSpeak: boolean
  isBusy: boolean
  joinVoice: () => Promise<void>
  leaveVoice: () => Promise<void>
  refreshPermissions: () => Promise<void>
  toggleMute: () => Promise<void>
}

export function useOfficeHoursVoice(
  sessionId: string | null,
): UseOfficeHoursVoiceResult {
  const liveKit = useLiveKitRoom()
  const connect = liveKit.connect
  const disconnect = liveKit.disconnect
  const [isBusy, setIsBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [canSpeak, setCanSpeak] = useState(false)

  const connectWithCurrentPermissions = useCallback(async () => {
    if (!sessionId) {
      return
    }
    setIsBusy(true)
    setActionError(null)
    try {
      const token = await fetchOfficeHoursMediaToken(sessionId)
      const connected = await connect(token)
      if (connected) setCanSpeak(token.canSpeak)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Unable to join Office Hours voice.')
    } finally {
      setIsBusy(false)
    }
  }, [connect, sessionId])

  const joinVoice = useCallback(async () => {
    await connectWithCurrentPermissions()
  }, [connectWithCurrentPermissions])

  const leaveVoice = useCallback(async () => {
    setIsBusy(true)
    setActionError(null)
    try {
      await disconnect()
      setCanSpeak(false)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Unable to leave Office Hours voice.')
    } finally {
      setIsBusy(false)
    }
  }, [disconnect])

  return {
    status: liveKit.status,
    error: actionError ?? liveKit.error,
    isMuted: liveKit.isMuted,
    canSpeak,
    isBusy,
    joinVoice,
    leaveVoice,
    refreshPermissions: connectWithCurrentPermissions,
    toggleMute: liveKit.toggleMute,
  }
}
