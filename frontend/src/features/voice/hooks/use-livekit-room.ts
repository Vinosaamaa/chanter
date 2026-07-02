import { useCallback, useEffect, useRef, useState } from 'react'
import { Room } from 'livekit-client'

import type { VoiceConnectionStatus, VoiceMediaToken } from '../voice-types'

type UseLiveKitRoomResult = {
  status: VoiceConnectionStatus
  error: string | null
  isMuted: boolean
  connect: (token: VoiceMediaToken) => Promise<boolean>
  disconnect: () => Promise<void>
  toggleMute: () => Promise<void>
}

export function useLiveKitRoom(): UseLiveKitRoomResult {
  const roomRef = useRef<Room | null>(null)
  const [status, setStatus] = useState<VoiceConnectionStatus>('idle')
  const [error, setError] = useState<string | null>(null)
  const [isMuted, setIsMuted] = useState(false)

  const disconnect = useCallback(async () => {
    const room = roomRef.current
    roomRef.current = null
    if (room) {
      try {
        await room.disconnect()
      } catch {
        // Ignore cleanup failures during disconnect.
      }
    }
    setStatus('idle')
    setIsMuted(false)
    setError(null)
  }, [])

  const connect = useCallback(async (token: VoiceMediaToken) => {
    setStatus('connecting')
    setError(null)
    try {
      await disconnect()
      const room = new Room({
        adaptiveStream: true,
        dynacast: true,
      })
      await room.connect(token.serverUrl, token.participantToken)
      try {
        if (token.canSpeak) {
          await room.localParticipant.setMicrophoneEnabled(true)
          setIsMuted(false)
        } else {
          await room.localParticipant.setMicrophoneEnabled(false)
          setIsMuted(true)
        }
      } catch (micError) {
        await room.disconnect()
        throw micError
      }
      roomRef.current = room
      setStatus('connected')
      return true
    } catch (caught) {
      setStatus('error')
      setError(caught instanceof Error ? caught.message : 'Unable to connect to voice.')
      return false
    }
  }, [disconnect])

  const toggleMute = useCallback(async () => {
    const room = roomRef.current
    if (!room) {
      return
    }
    const nextMuted = !isMuted
    await room.localParticipant.setMicrophoneEnabled(!nextMuted)
    setIsMuted(nextMuted)
  }, [isMuted])

  useEffect(() => {
    return () => {
      void disconnect()
    }
  }, [disconnect])

  return {
    status,
    error,
    isMuted,
    connect,
    disconnect,
    toggleMute,
  }
}
