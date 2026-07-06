import { useCallback, useEffect, useState } from 'react'

import {
  fetchVoiceChannelMediaToken,
  fetchVoicePresences,
  leaveVoiceChannel,
} from '../voice-api'
import type { VoiceConnectionStatus, VoicePresence } from '../voice-types'

import { useLiveKitRoom } from './use-livekit-room'

type UseVoiceChannelResult = {
  presences: VoicePresence[]
  status: VoiceConnectionStatus
  error: string | null
  isMuted: boolean
  isBusy: boolean
  joinVoice: () => Promise<void>
  leaveVoice: () => Promise<void>
  toggleMute: () => Promise<void>
  refreshPresences: () => Promise<void>
}

export function useVoiceChannel(channelId: string): UseVoiceChannelResult {
  const liveKit = useLiveKitRoom()
  const [presences, setPresences] = useState<VoicePresence[]>([])
  const [isBusy, setIsBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const refreshPresences = useCallback(async () => {
    const nextPresences = await fetchVoicePresences(channelId)
    setPresences(nextPresences)
  }, [channelId])

  useEffect(() => {
    let cancelled = false

    void fetchVoicePresences(channelId)
      .then((nextPresences) => {
        if (!cancelled) {
          setPresences(nextPresences)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPresences([])
        }
      })

    return () => {
      cancelled = true
    }
  }, [channelId])

  const joinVoice = useCallback(async () => {
    setIsBusy(true)
    setActionError(null)
    try {
      const token = await fetchVoiceChannelMediaToken(channelId)
      const connected = await liveKit.connect(token)
      if (connected) {
        await refreshPresences()
      }
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Unable to join voice.')
    } finally {
      setIsBusy(false)
    }
  }, [channelId, liveKit, refreshPresences])

  const leaveVoice = useCallback(async () => {
    setIsBusy(true)
    setActionError(null)
    try {
      await leaveVoiceChannel(channelId)
      await liveKit.disconnect()
      await refreshPresences()
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Unable to leave voice.')
    } finally {
      setIsBusy(false)
    }
  }, [channelId, liveKit, refreshPresences])

  useEffect(() => {
    if (liveKit.status !== 'connected') {
      return undefined
    }

    const intervalId = window.setInterval(() => {
      void refreshPresences()
    }, 10_000)

    return () => {
      window.clearInterval(intervalId)
    }
  }, [liveKit.status, refreshPresences])

  return {
    presences,
    status: liveKit.status,
    error: actionError ?? liveKit.error,
    isMuted: liveKit.isMuted,
    isBusy,
    joinVoice,
    leaveVoice,
    toggleMute: liveKit.toggleMute,
    refreshPresences,
  }
}
