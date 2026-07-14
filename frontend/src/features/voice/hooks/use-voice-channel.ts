import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

import {
  fetchVoiceChannelMediaToken,
  fetchVoicePresences,
  joinVoiceChannel,
  leaveVoiceChannel,
  type VoiceChannelScope,
} from '../voice-api'
import type { VoiceConnectionStatus, VoicePresence } from '../voice-types'

import { useLiveKitRoom } from './use-livekit-room'

type UseVoiceChannelResult = {
  presences: VoicePresence[]
  status: VoiceConnectionStatus
  error: string | null
  isMuted: boolean
  isBusy: boolean
  isLoadingPresences: boolean
  presenceError: string | null
  joinVoice: () => Promise<void>
  leaveVoice: () => Promise<void>
  toggleMute: () => Promise<void>
  refreshPresences: () => Promise<void>
}

type VoicePresenceState = {
  key: string
  presences: VoicePresence[]
  error: string | null
  isLoading: boolean
}

export function useVoiceChannel(
  channelId: string,
  scope: VoiceChannelScope = 'study',
): UseVoiceChannelResult {
  const liveKit = useLiveKitRoom()
  const connect = liveKit.connect
  const disconnect = liveKit.disconnect
  const [isBusy, setIsBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const sessionKey = `${scope}:${channelId}`
  const [presenceState, setPresenceState] = useState<VoicePresenceState>(
    () => createPresenceState(sessionKey),
  )
  const sessionKeyRef = useRef(sessionKey)
  const operationRef = useRef(0)
  const presencePublishedRef = useRef(false)
  const currentPresenceState = presenceState.key === sessionKey
    ? presenceState
    : createPresenceState(sessionKey)

  useLayoutEffect(() => {
    sessionKeyRef.current = sessionKey
  }, [sessionKey])

  const refreshPresences = useCallback(async () => {
    const requestKey = sessionKey
    try {
      const nextPresences = await fetchVoicePresences(channelId, scope)
      if (sessionKeyRef.current === requestKey) {
        setPresenceState({
          key: requestKey,
          presences: nextPresences,
          error: null,
          isLoading: false,
        })
      }
    } catch (caught) {
      if (sessionKeyRef.current === requestKey) {
        setPresenceState({
          key: requestKey,
          presences: [],
          error: caught instanceof Error ? caught.message : 'Unable to load voice participants.',
          isLoading: false,
        })
      }
      throw caught
    }
  }, [channelId, scope, sessionKey])

  useEffect(() => {
    let cancelled = false
    const requestKey = sessionKey
    void fetchVoicePresences(channelId, scope)
      .then((nextPresences) => {
        if (!cancelled && sessionKeyRef.current === requestKey) {
          setPresenceState({
            key: requestKey,
            presences: nextPresences,
            error: null,
            isLoading: false,
          })
        }
      })
      .catch((caught) => {
        if (!cancelled && sessionKeyRef.current === requestKey) {
          setPresenceState({
            key: requestKey,
            presences: [],
            error: caught instanceof Error ? caught.message : 'Unable to load voice participants.',
            isLoading: false,
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [channelId, scope, sessionKey])

  useEffect(() => {
    presencePublishedRef.current = false
    return () => {
      operationRef.current += 1
      void disconnect()
      if (presencePublishedRef.current) {
        void leaveVoiceChannel(channelId, scope)
      }
      presencePublishedRef.current = false
    }
  }, [channelId, disconnect, scope])

  const joinVoice = useCallback(async () => {
    const requestKey = sessionKey
    const operation = operationRef.current + 1
    operationRef.current = operation
    let mediaConnected = false
    setIsBusy(true)
    setActionError(null)
    try {
      const token = await fetchVoiceChannelMediaToken(channelId, scope)
      if (sessionKeyRef.current !== requestKey || operationRef.current !== operation) return
      mediaConnected = await connect(token)
      if (!mediaConnected) return
      if (sessionKeyRef.current !== requestKey || operationRef.current !== operation) {
        await disconnect()
        return
      }
      await joinVoiceChannel(channelId, scope)
      presencePublishedRef.current = true
      await refreshPresences().catch(() => undefined)
    } catch (caught) {
      if (mediaConnected) {
        await disconnect()
        void leaveVoiceChannel(channelId, scope)
        presencePublishedRef.current = false
      }
      if (sessionKeyRef.current === requestKey && operationRef.current === operation) {
        setActionError(caught instanceof Error ? caught.message : 'Unable to join voice.')
      }
    } finally {
      if (sessionKeyRef.current === requestKey && operationRef.current === operation) {
        setIsBusy(false)
      }
    }
  }, [channelId, connect, disconnect, refreshPresences, scope, sessionKey])

  const leaveVoice = useCallback(async () => {
    const requestKey = sessionKey
    const operation = operationRef.current + 1
    operationRef.current = operation
    setIsBusy(true)
    setActionError(null)
    await disconnect()
    try {
      await leaveVoiceChannel(channelId, scope)
      presencePublishedRef.current = false
      await refreshPresences()
    } catch (caught) {
      if (sessionKeyRef.current === requestKey && operationRef.current === operation) {
        setActionError(caught instanceof Error ? caught.message : 'Unable to leave voice.')
      }
    } finally {
      if (sessionKeyRef.current === requestKey && operationRef.current === operation) {
        setIsBusy(false)
      }
    }
  }, [channelId, disconnect, refreshPresences, scope, sessionKey])

  useEffect(() => {
    if (liveKit.status !== 'connected') {
      return undefined
    }

    const intervalId = window.setInterval(() => {
      void joinVoiceChannel(channelId, scope)
        .then(() => {
          presencePublishedRef.current = true
          return refreshPresences()
        })
        .catch(() => {
          setActionError('Unable to refresh voice presence.')
        })
    }, 10_000)

    return () => {
      window.clearInterval(intervalId)
    }
  }, [channelId, liveKit.status, refreshPresences, scope])

  return {
    presences: currentPresenceState.presences,
    status: liveKit.status,
    error: actionError ?? currentPresenceState.error ?? liveKit.error,
    isMuted: liveKit.isMuted,
    isBusy,
    isLoadingPresences: currentPresenceState.isLoading,
    presenceError: currentPresenceState.error,
    joinVoice,
    leaveVoice,
    toggleMute: liveKit.toggleMute,
    refreshPresences,
  }
}

function createPresenceState(key: string): VoicePresenceState {
  return {
    key,
    presences: [],
    error: null,
    isLoading: true,
  }
}
