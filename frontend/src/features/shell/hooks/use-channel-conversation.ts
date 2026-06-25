import { useEffect, useMemo, useRef, useState } from 'react'

import { useAuthStore } from '../../../stores/auth-store'
import { RealtimeClient, type RealtimeConnectionStatus } from '../../realtime/realtime-client'
import type { ChannelMessage, ChannelScope } from '../channel-message-types'
import { fetchChannelMessages } from '../channel-messages-api'

type UseChannelConversationResult = {
  messages: ChannelMessage[]
  connectionStatus: RealtimeConnectionStatus
  isLoadingHistory: boolean
  error: string | null
  sendMessage: (body: string) => Promise<boolean>
  isSending: boolean
}

export function useChannelConversation(
  channelScope: ChannelScope | null,
  channelId: string | undefined,
): UseChannelConversationResult {
  const accessToken = useAuthStore((state) => state.accessToken)
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [messages, setMessages] = useState<ChannelMessage[]>([])
  const [connectionStatus, setConnectionStatus] =
    useState<RealtimeConnectionStatus>('connecting')
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isSending, setIsSending] = useState(false)
  const clientRef = useRef<RealtimeClient | null>(null)
  const lastEventAtRef = useRef<string | null>(null)
  const lastEventIdRef = useRef<string | null>(null)

  const canConnect = Boolean(channelScope && channelId && accessToken)

  useEffect(() => {
    if (!canConnect || !channelScope || !channelId || !accessToken) {
      return
    }

    let cancelled = false

    void fetchChannelMessages(channelScope, channelId)
      .then((response) => {
        if (!cancelled) {
          setMessages((current) => mergeMessages(current, response.messages))
          const last = response.messages.at(-1)
          lastEventAtRef.current = last?.createdAt ?? null
          lastEventIdRef.current = last?.id ?? null
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'Unable to load channel history')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoadingHistory(false)
        }
      })

    const client = new RealtimeClient({
      accessToken,
      channelId,
      channelScope,
      onMessage: (message) => {
        setMessages((current) => {
          const withoutMatchingOptimistic = current.filter(
            (entry) =>
              !entry.id.startsWith('optimistic-') ||
              entry.body !== message.body ||
              entry.senderUserId !== message.senderUserId,
          )
          if (withoutMatchingOptimistic.some((entry) => entry.id === message.id)) {
            return withoutMatchingOptimistic
          }
          return [...withoutMatchingOptimistic, message]
        })
        lastEventAtRef.current = message.createdAt
        lastEventIdRef.current = message.id
      },
      onStatusChange: (status) => {
        if (status === 'connected') {
          setError((current) => (isRealtimeConnectionError(current) ? null : current))
        }
        if (status === 'connected' && lastEventAtRef.current) {
          void fetchChannelMessages(
            channelScope,
            channelId,
            lastEventAtRef.current,
            lastEventIdRef.current ?? undefined,
          )
            .then((response) => {
              if (response.messages.length === 0) {
                return
              }
              setMessages((current) => mergeMessages(current, response.messages))
              const last = response.messages.at(-1)
              if (last) {
                lastEventAtRef.current = last.createdAt
                lastEventIdRef.current = last.id
              }
            })
            .catch(() => {
              setError('Unable to reconcile missed messages after reconnect')
            })
        }
        setConnectionStatus(status)
      },
      onError: (message) => setError(message),
    })

    clientRef.current = client
    client.connect()

    return () => {
      cancelled = true
      client.disconnect()
      clientRef.current = null
    }
  }, [accessToken, canConnect, channelId, channelScope])

  const sendMessage = useMemo(
    () => async (body: string) => {
      if (!channelScope || !channelId || !userId) {
        return false
      }

      const trimmed = body.trim()
      if (!trimmed) {
        return false
      }

      const optimisticId = `optimistic-${crypto.randomUUID()}`
      const optimisticMessage: ChannelMessage = {
        id: optimisticId,
        channelId,
        senderUserId: userId,
        body: trimmed,
        createdAt: new Date().toISOString(),
      }

      setIsSending(true)
      setError(null)
      setMessages((current) => [...current, optimisticMessage])

      try {
        if (!clientRef.current) {
          throw new Error('Realtime connection is not ready')
        }
        clientRef.current.send(trimmed)
        return true
      } catch (caught) {
        setMessages((current) => current.filter((message) => message.id !== optimisticId))
        setError(caught instanceof Error ? caught.message : 'Unable to send message')
        return false
      } finally {
        setIsSending(false)
      }
    },
    [channelId, channelScope, userId],
  )

  return {
    messages,
    connectionStatus,
    isLoadingHistory,
    error,
    sendMessage,
    isSending,
  }
}

function mergeMessages(current: ChannelMessage[], fetched: ChannelMessage[]): ChannelMessage[] {
  const knownIds = new Set(current.map((entry) => entry.id))
  const merged = [...current]
  for (const message of fetched) {
    if (!knownIds.has(message.id)) {
      merged.push(message)
    }
  }
  return merged.sort((left, right) => left.createdAt.localeCompare(right.createdAt))
}

function isRealtimeConnectionError(message: string | null): boolean {
  return (
    message === 'Realtime connection failed' ||
    message === 'Received an invalid realtime event' ||
    message === 'Realtime connection is not ready'
  )
}
