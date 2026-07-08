import { useEffect, useMemo, useRef, useState } from 'react'

import { useAuthStore } from '../../../stores/auth-store'

import { fetchDirectMessages, fetchFriends, sendDirectMessage } from '../friends-api'
import {
  SocialRealtimeClient,
  type SocialRealtimeConnectionStatus,
} from '../social-realtime-client'
import type { DirectMessage, FriendPresenceStatus, FriendSummary } from '../types'

type UseFriendsHubResult = {
  friends: FriendSummary[]
  selectedFriendId: string | null
  selectFriend: (friendUserId: string) => void
  messages: DirectMessage[]
  presenceByFriendId: Record<string, FriendPresenceStatus>
  connectionStatus: SocialRealtimeConnectionStatus
  isLoadingFriends: boolean
  isLoadingMessages: boolean
  error: string | null
  sendMessage: (body: string) => Promise<boolean>
  isSending: boolean
}

const MAX_MESSAGE_BODY_LENGTH = 4000

export function useFriendsHub(): UseFriendsHubResult {
  const accessToken = useAuthStore((state) => state.accessToken)
  const currentUserId = useAuthStore((state) => state.user?.id ?? null)
  const [friends, setFriends] = useState<FriendSummary[]>([])
  const [selectedFriendId, setSelectedFriendId] = useState<string | null>(null)
  const [messages, setMessages] = useState<DirectMessage[]>([])
  const [presenceByFriendId, setPresenceByFriendId] = useState<Record<string, FriendPresenceStatus>>(
    {},
  )
  const [connectionStatus, setConnectionStatus] =
    useState<SocialRealtimeConnectionStatus>('connecting')
  const [isLoadingFriends, setIsLoadingFriends] = useState(true)
  const [isLoadingMessages, setIsLoadingMessages] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSending, setIsSending] = useState(false)
  const clientRef = useRef<SocialRealtimeClient | null>(null)

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
          setError(caught instanceof Error ? caught.message : 'Unable to load friends')
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
      setMessages([])
      return
    }

    let cancelled = false
    setIsLoadingMessages(true)

    void fetchDirectMessages(selectedFriendId)
      .then((response) => {
        if (!cancelled) {
          setMessages(response.messages)
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'Unable to load direct messages')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoadingMessages(false)
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
        if (
          selectedFriendId &&
          (message.senderUserId === selectedFriendId || message.recipientUserId === selectedFriendId)
        ) {
          setMessages((current) => mergeMessages(current, [message]))
        }
      },
      onPresenceChange: (userId, status) => {
        setPresenceByFriendId((current) => ({
          ...current,
          [userId]: status,
        }))
      },
      onStatusChange: setConnectionStatus,
      onError: (message) => setError(message),
    })

    clientRef.current = client
    client.connect()

    return () => {
      client.disconnect()
      clientRef.current = null
    }
  }, [accessToken, selectedFriendId])

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

    setMessages((current) => [...current, optimisticMessage])
    setIsSending(true)
    setError(null)

    try {
      const client = clientRef.current
      if (client && connectionStatus === 'connected') {
        client.sendDirectMessage(selectedFriendId, trimmed)
      } else {
        await sendDirectMessage(selectedFriendId, trimmed)
        const refreshed = await fetchDirectMessages(selectedFriendId)
        setMessages(refreshed.messages)
      }
      return true
    } catch (caught) {
      setMessages((current) => current.filter((message) => message.id !== optimisticId))
      setError(caught instanceof Error ? caught.message : 'Unable to send direct message')
      return false
    } finally {
      setIsSending(false)
    }
  }

  const presenceMap = useMemo(() => {
    const next: Record<string, FriendPresenceStatus> = { ...presenceByFriendId }
    for (const friend of friends) {
      next[friend.friendUserId] ??= 'offline'
    }
    return next
  }, [friends, presenceByFriendId])

  return {
    friends,
    selectedFriendId,
    selectFriend: setSelectedFriendId,
    messages,
    presenceByFriendId: presenceMap,
    connectionStatus,
    isLoadingFriends,
    isLoadingMessages,
    error,
    sendMessage,
    isSending,
  }
}

function mergeMessages(current: DirectMessage[], incoming: DirectMessage[]): DirectMessage[] {
  let next = [...current]
  for (const message of incoming) {
    next = next.filter(
      (entry) =>
        !entry.id.startsWith('optimistic-') ||
        entry.body !== message.body ||
        entry.senderUserId !== message.senderUserId,
    )
    if (!next.some((entry) => entry.id === message.id)) {
      next = [...next, message]
    }
  }
  return next
}
