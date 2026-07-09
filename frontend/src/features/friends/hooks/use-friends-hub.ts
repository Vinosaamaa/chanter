import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { useAuthStore } from '../../../stores/auth-store'
import { useLiveKitRoom } from '../../voice/hooks/use-livekit-room'

import { fetchDirectMessageCallMediaToken } from '../dm-call-api'
import { fetchDirectMessages, fetchFriends, sendDirectMessage } from '../friends-api'
import {
  SocialRealtimeClient,
  type SocialRealtimeConnectionStatus,
} from '../social-realtime-client'
import type {
  DirectMessage,
  DmCallState,
  FriendPresenceStatus,
  FriendSummary,
  SocialCallMessage,
} from '../types'

const initialCallState: DmCallState = {
  phase: 'idle',
  callId: null,
  peerUserId: null,
  reason: null,
}

type UseFriendsHubResult = {
  friends: FriendSummary[]
  selectedFriendId: string | null
  selectFriend: (friendUserId: string) => void
  messages: DirectMessage[]
  presenceByFriendId: Record<string, FriendPresenceStatus>
  connectionStatus: SocialRealtimeConnectionStatus
  isLoadingFriends: boolean
  isLoadingMessages: boolean
  friendsListError: string | null
  error: string | null
  sendMessage: (body: string) => Promise<boolean>
  isSending: boolean
  callState: DmCallState
  callError: string | null
  isMuted: boolean
  startCall: () => void
  acceptCall: () => void
  declineCall: () => void
  hangUpCall: () => void
  toggleCallMute: () => Promise<void>
}

const MAX_MESSAGE_BODY_LENGTH = 4000

export function useFriendsHub(): UseFriendsHubResult {
  const accessToken = useAuthStore((state) => state.accessToken)
  const currentUserId = useAuthStore((state) => state.user?.id ?? null)
  const [friends, setFriends] = useState<FriendSummary[]>([])
  const [selectedFriendId, setSelectedFriendId] = useState<string | null>(null)
  const [loadedMessages, setLoadedMessages] = useState<DirectMessage[]>([])
  const [loadedMessagesFriendId, setLoadedMessagesFriendId] = useState<string | null>(null)
  const [presenceByFriendId, setPresenceByFriendId] = useState<Record<string, FriendPresenceStatus>>(
    {},
  )
  const [connectionStatus, setConnectionStatus] =
    useState<SocialRealtimeConnectionStatus>('connecting')
  const [isLoadingFriends, setIsLoadingFriends] = useState(true)
  const [friendsListError, setFriendsListError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSending, setIsSending] = useState(false)
  const [callState, setCallState] = useState<DmCallState>(initialCallState)
  const [callError, setCallError] = useState<string | null>(null)
  const clientRef = useRef<SocialRealtimeClient | null>(null)
  const callStateRef = useRef<DmCallState>(initialCallState)
  const resetCallTimerRef = useRef<number | null>(null)
  const inviteInFlightRef = useRef(false)
  const livekitErrorRef = useRef<string | null>(null)
  const livekit = useLiveKitRoom()
  const { connect: connectLivekit, disconnect: disconnectLivekit, toggleMute, isMuted, error: livekitConnectionError } =
    livekit
  const loadedMessagesFriendIdRef = useRef<string | null>(null)
  const selectedFriendIdRef = useRef<string | null>(null)

  useEffect(() => {
    loadedMessagesFriendIdRef.current = loadedMessagesFriendId
  }, [loadedMessagesFriendId])

  useEffect(() => {
    selectedFriendIdRef.current = selectedFriendId
  }, [selectedFriendId])

  useEffect(() => {
    callStateRef.current = callState
  }, [callState])

  useEffect(() => {
    livekitErrorRef.current = livekitConnectionError
  }, [livekitConnectionError])

  const resetCall = useCallback(async (reason: string | null = null) => {
    if (resetCallTimerRef.current !== null) {
      window.clearTimeout(resetCallTimerRef.current)
      resetCallTimerRef.current = null
    }
    inviteInFlightRef.current = false
    await disconnectLivekit()
    setCallState({ phase: 'ended', callId: null, peerUserId: null, reason })
    const delayMs = reason === null ? 300 : 1_500
    resetCallTimerRef.current = window.setTimeout(() => {
      setCallState(initialCallState)
      setCallError(null)
      resetCallTimerRef.current = null
    }, delayMs)
  }, [disconnectLivekit])

  const joinCallMedia = useCallback(async (callId: string) => {
    setCallState((current) => ({ ...current, phase: 'connecting' }))
    setCallError(null)
    try {
      const token = await fetchDirectMessageCallMediaToken(callId)
      const connected = await connectLivekit(token)
      if (!connected) {
        setCallError(livekitErrorRef.current ?? 'Unable to join call audio')
        await resetCall('media_failed')
        return
      }
      setCallState((current) => ({ ...current, phase: 'in_call' }))
    } catch (caught) {
      setCallError(caught instanceof Error ? caught.message : 'Unable to join call audio')
      await resetCall('media_failed')
    }
  }, [connectLivekit, resetCall])

  const handleCallEvent = useCallback((event: SocialCallMessage) => {
    if (event.type === 'call_ringing') {
      inviteInFlightRef.current = false
      setCallError(null)
      const peerUserId =
        event.direction === 'incoming' ? event.callerUserId : event.calleeUserId
      setCallState({
        phase: event.direction === 'incoming' ? 'incoming_ringing' : 'outgoing_ringing',
        callId: event.callId,
        peerUserId,
        reason: null,
      })
      return
    }

    if (event.type === 'call_busy') {
      setCallError('Friend is busy on another call')
      void resetCall('busy')
      return
    }

    if (event.type === 'call_accepted') {
      const current = callStateRef.current
      const peerUserId =
        current.peerUserId ??
        (event.callerUserId === currentUserId ? event.calleeUserId : event.callerUserId)
      setCallState({
        phase: 'connecting',
        callId: event.callId,
        peerUserId,
        reason: null,
      })
      void joinCallMedia(event.callId)
      return
    }

    if (event.type === 'call_ended') {
      void resetCall(event.reason)
    }
  }, [currentUserId, joinCallMedia, resetCall])

  useEffect(() => {
    let cancelled = false

    void fetchFriends()
      .then((response) => {
        if (!cancelled) {
          setFriends(response.friends)
          if (response.friends.length > 0) {
            setSelectedFriendId((current) => current ?? response.friends[0]?.friendUserId ?? null)
          }
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setFriendsListError(
            caught instanceof Error ? caught.message : 'Unable to load friends',
          )
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoadingFriends(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!selectedFriendId) {
      return
    }

    let cancelled = false

    void fetchDirectMessages(selectedFriendId)
      .then((response) => {
        if (!cancelled) {
          setLoadedMessages((current) =>
            loadedMessagesFriendIdRef.current === selectedFriendId && current.length > 0
              ? mergeMessages(response.messages, current)
              : response.messages,
          )
          setLoadedMessagesFriendId(selectedFriendId)
          setError(null)
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setLoadedMessages([])
          setLoadedMessagesFriendId(selectedFriendId)
          setError(caught instanceof Error ? caught.message : 'Unable to load direct messages')
        }
      })

    return () => {
      cancelled = true
    }
  }, [selectedFriendId])

  useEffect(() => {
    if (!accessToken) {
      return
    }

    const client = new SocialRealtimeClient({
      accessToken,
      onDirectMessage: (message) => {
        const friendId = selectedFriendIdRef.current
        if (
          !friendId ||
          (message.senderUserId !== friendId && message.recipientUserId !== friendId)
        ) {
          return
        }

        const ownedFriendId = loadedMessagesFriendIdRef.current
        if (ownedFriendId === friendId) {
          setLoadedMessages((current) => mergeMessages(current, [message]))
          return
        }

        setLoadedMessages([message])
        setLoadedMessagesFriendId(friendId)
      },
      onPresenceChange: (userId, status) => {
        setPresenceByFriendId((current) => ({
          ...current,
          [userId]: status,
        }))
      },
      onCallEvent: handleCallEvent,
      onStatusChange: (status) => {
        setConnectionStatus(status)
        if (status === 'connected') {
          const friendId = selectedFriendIdRef.current
          if (!friendId) {
            return
          }
          void fetchDirectMessages(friendId)
            .then((response) => {
              if (selectedFriendIdRef.current !== friendId) {
                return
              }
              setLoadedMessages((current) => mergeMessages(response.messages, current))
              setLoadedMessagesFriendId(friendId)
            })
            .catch(() => {
              // Keep existing thread state when reconnect reconciliation fails.
            })
        }
      },
      onError: (message) => setError(message),
    })

    clientRef.current = client
    client.connect()

    return () => {
      client.disconnect()
      clientRef.current = null
      void disconnectLivekit()
    }
  }, [accessToken, disconnectLivekit, handleCallEvent])

  const reconcileThreadMessages = async (friendId: string, sent: DirectMessage): Promise<void> => {
    if (selectedFriendIdRef.current !== friendId) {
      return
    }

    setLoadedMessages((current) => mergeMessages(current, [sent]))
    setLoadedMessagesFriendId(friendId)

    try {
      const refreshed = await fetchDirectMessages(friendId)
      if (selectedFriendIdRef.current !== friendId) {
        return
      }
      setLoadedMessages((current) => mergeMessages(refreshed.messages, current))
      setLoadedMessagesFriendId(friendId)
    } catch {
      // Send succeeded; keep optimistic/POST state when history refresh fails.
    }
  }

  const sendMessage = async (body: string): Promise<boolean> => {
    const trimmed = body.trim()
    if (!trimmed || !selectedFriendId || !currentUserId) {
      return false
    }
    if (trimmed.length > MAX_MESSAGE_BODY_LENGTH) {
      setError(`Messages must be ${MAX_MESSAGE_BODY_LENGTH} characters or fewer`)
      return false
    }

    const optimisticId = `optimistic-${crypto.randomUUID()}`
    const optimisticMessage: DirectMessage = {
      id: optimisticId,
      senderUserId: currentUserId,
      recipientUserId: selectedFriendId,
      body: trimmed,
      sentAt: new Date().toISOString(),
    }

    const friendId = selectedFriendId
    const ownedFriendId = loadedMessagesFriendIdRef.current
    setLoadedMessages(
      ownedFriendId === friendId ? (current) => [...current, optimisticMessage] : [optimisticMessage],
    )
    setLoadedMessagesFriendId(friendId)
    setIsSending(true)
    setError(null)

    try {
      const client = clientRef.current
      if (client && connectionStatus === 'connected') {
        try {
          await client.sendDirectMessage(friendId, trimmed)
        } catch (wsError) {
          const wsMessage = wsError instanceof Error ? wsError.message : ''
          if (wsMessage === 'Direct message send timed out') {
            try {
              const refreshed = await fetchDirectMessages(friendId)
              if (selectedFriendIdRef.current === friendId) {
                setLoadedMessages((current) => mergeMessages(refreshed.messages, current))
                setLoadedMessagesFriendId(friendId)
                const alreadySent = refreshed.messages.some(
                  (message) =>
                    message.body === trimmed && message.senderUserId === currentUserId,
                )
                if (alreadySent) {
                  return true
                }
              }
            } catch {
              // Fall through to HTTP send when reconciliation cannot confirm delivery.
            }
          }
          const sent = await sendDirectMessage(friendId, trimmed)
          await reconcileThreadMessages(friendId, sent)
        }
      } else {
        const sent = await sendDirectMessage(friendId, trimmed)
        await reconcileThreadMessages(friendId, sent)
      }
      return true
    } catch (caught) {
      setLoadedMessages((current) => current.filter((message) => message.id !== optimisticId))
      setError(caught instanceof Error ? caught.message : 'Unable to send direct message')
      return false
    } finally {
      setIsSending(false)
    }
  }

  const messages = useMemo(
    () =>
      selectedFriendId && loadedMessagesFriendId === selectedFriendId ? loadedMessages : [],
    [loadedMessages, loadedMessagesFriendId, selectedFriendId],
  )

  const isLoadingMessages = Boolean(
    selectedFriendId && loadedMessagesFriendId !== selectedFriendId,
  )

  const presenceMap = useMemo(() => {
    const next: Record<string, FriendPresenceStatus> = { ...presenceByFriendId }
    for (const friend of friends) {
      next[friend.friendUserId] ??= 'offline'
    }
    return next
  }, [friends, presenceByFriendId])

  const startCall = () => {
    if (!selectedFriendId || connectionStatus !== 'connected' || inviteInFlightRef.current) {
      return
    }
    if (callStateRef.current.phase !== 'idle') {
      return
    }
    inviteInFlightRef.current = true
    setCallError(null)
    clientRef.current?.inviteCall(selectedFriendId)
  }

  const acceptCall = () => {
    const callId = callStateRef.current.callId
    if (!callId) {
      return
    }
    setCallError(null)
    clientRef.current?.acceptCall(callId)
  }

  const declineCall = () => {
    const { callId, phase } = callStateRef.current
    if (!callId) {
      return
    }
    if (phase === 'outgoing_ringing') {
      clientRef.current?.cancelCall(callId)
    } else {
      clientRef.current?.declineCall(callId)
    }
    void resetCall(phase === 'outgoing_ringing' ? 'cancelled' : 'declined')
  }

  const hangUpCall = () => {
    const callId = callStateRef.current.callId
    if (callId) {
      clientRef.current?.endCall(callId)
    }
    void resetCall('ended')
  }

  return {
    friends,
    selectedFriendId,
    selectFriend: (friendUserId: string) => {
      setError(null)
      setSelectedFriendId(friendUserId)
    },
    messages,
    presenceByFriendId: presenceMap,
    connectionStatus,
    isLoadingFriends,
    isLoadingMessages,
    friendsListError,
    error,
    sendMessage,
    isSending,
    callState,
    callError,
    isMuted,
    startCall,
    acceptCall,
    declineCall,
    hangUpCall,
    toggleCallMute: async () => {
      try {
        await toggleMute()
      } catch (caught) {
        setCallError(caught instanceof Error ? caught.message : 'Unable to update microphone')
      }
    },
  }
}

function mergeMessages(current: DirectMessage[], incoming: DirectMessage[]): DirectMessage[] {
  let next = [...current]
  for (const message of incoming) {
    let removedOptimistic = false
    next = next.filter((entry) => {
      if (
        !removedOptimistic &&
        entry.id.startsWith('optimistic-') &&
        entry.body === message.body &&
        entry.senderUserId === message.senderUserId
      ) {
        removedOptimistic = true
        return false
      }
      return true
    })
    if (!next.some((entry) => entry.id === message.id)) {
      next = [...next, message]
    }
  }
  return next
}
