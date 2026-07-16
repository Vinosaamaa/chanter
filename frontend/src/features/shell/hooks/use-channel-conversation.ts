import { useEffect, useMemo, useRef, useState } from 'react'

import { getApiAccessToken, refreshApiSession } from '../../../lib/api-client'
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

type ChannelConversationState = {
  key: string | null
  messages: ChannelMessage[]
  connectionStatus: RealtimeConnectionStatus
  isLoadingHistory: boolean
  error: string | null
  isSending: boolean
}

const MAX_MESSAGE_BODY_LENGTH = 4000

export function useChannelConversation(
  channelScope: ChannelScope | null,
  channelId: string | undefined,
): UseChannelConversationResult {
  const accessToken = useAuthStore((state) => state.accessToken)
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const canConnect = Boolean(channelScope && channelId && accessToken)
  const conversationKey = channelScope && channelId
    ? `${channelScope}:${channelId}:${userId ?? 'anonymous'}`
    : null
  const [conversationState, setConversationState] = useState<ChannelConversationState>(
    () => createConversationState(conversationKey, canConnect),
  )
  const clientRef = useRef<RealtimeClient | null>(null)
  const clientKeyRef = useRef<string | null>(null)
  const lastEventAtRef = useRef<string | null>(null)
  const lastEventIdRef = useRef<string | null>(null)
  const currentState = conversationState.key === conversationKey
    ? conversationState
    : createConversationState(conversationKey, canConnect)

  useEffect(() => {
    lastEventAtRef.current = null
    lastEventIdRef.current = null

    if (!canConnect || !channelScope || !channelId || !accessToken) {
      return undefined
    }

    let cancelled = false
    let historyReady = false
    let realtimeConnected = false
    let initialReconcileStarted = false
    let historyCursorAt: string | null = null
    let historyCursorId: string | null = null
    const updateState = (
      update: (current: ChannelConversationState) => ChannelConversationState,
    ) => {
      if (cancelled) return
      setConversationState((current) => update(
        current.key === conversationKey
          ? current
          : createConversationState(conversationKey, true),
      ))
    }

    const advanceCursor = (message: ChannelMessage | undefined) => {
      if (!message) return
      const currentAt = lastEventAtRef.current
      const currentId = lastEventIdRef.current
      if (
        !currentAt ||
        message.createdAt > currentAt ||
        (message.createdAt === currentAt && (!currentId || message.id > currentId))
      ) {
        lastEventAtRef.current = message.createdAt
        lastEventIdRef.current = message.id
      }
    }

    const reconcileFrom = (
      since: string | null,
      afterMessageId: string | null,
      initial: boolean,
    ) => {
      if (initial) initialReconcileStarted = true
      void fetchChannelMessages(
        channelScope,
        channelId,
        since ?? undefined,
        afterMessageId ?? undefined,
      )
        .then((response) => {
          updateState((current) => ({
            ...current,
            messages: mergeMessages(current.messages, response.messages),
          }))
          advanceCursor(response.messages.at(-1))
        })
        .catch(() => {
          if (initial) initialReconcileStarted = false
          updateState((current) => ({
            ...current,
            error: 'Unable to reconcile missed messages after reconnect',
          }))
        })
    }

    const reconcileInitialHistory = () => {
      if (!historyReady || !realtimeConnected || initialReconcileStarted) return
      reconcileFrom(historyCursorAt, historyCursorId, true)
    }

    void fetchChannelMessages(channelScope, channelId)
      .then((response) => {
        if (!cancelled) {
          updateState((current) => ({
            ...current,
            messages: mergeMessages(current.messages, response.messages),
          }))
          const last = response.messages.at(-1)
          historyCursorAt = last?.createdAt ?? null
          historyCursorId = last?.id ?? null
          advanceCursor(last)
        }
      })
      .catch((caught) => {
        updateState((current) => ({
          ...current,
          error: caught instanceof Error ? caught.message : 'Unable to load channel history',
        }))
      })
      .finally(() => {
        historyReady = true
        updateState((current) => ({ ...current, isLoadingHistory: false }))
        reconcileInitialHistory()
      })

    const client = new RealtimeClient({
      getAccessToken: getApiAccessToken,
      refreshSession: refreshApiSession,
      channelId,
      channelScope,
      onMessage: (message) => {
        updateState((current) => {
          const withoutMatchingOptimistic = current.messages.filter(
            (entry) =>
              !entry.id.startsWith('optimistic-') ||
              entry.body !== message.body ||
              entry.senderUserId !== message.senderUserId,
          )
          if (withoutMatchingOptimistic.some((entry) => entry.id === message.id)) {
            return { ...current, messages: withoutMatchingOptimistic }
          }
          return { ...current, messages: [...withoutMatchingOptimistic, message] }
        })
        advanceCursor(message)
      },
      onStatusChange: (status) => {
        realtimeConnected = status === 'connected'
        updateState((current) => ({
          ...current,
          connectionStatus: status,
          error: status === 'connected' && isRealtimeConnectionError(current.error)
            ? null
            : current.error,
        }))
        if (status === 'connected') {
          if (!initialReconcileStarted) {
            reconcileInitialHistory()
          } else if (historyReady) {
            reconcileFrom(lastEventAtRef.current, lastEventIdRef.current, false)
          }
        }
      },
      onError: (message) => updateState((current) => ({ ...current, error: message })),
    })

    clientRef.current = client
    clientKeyRef.current = conversationKey
    client.connect()

    return () => {
      cancelled = true
      client.disconnect()
      if (clientKeyRef.current === conversationKey) {
        clientRef.current = null
        clientKeyRef.current = null
      }
    }
  }, [accessToken, canConnect, channelId, channelScope, conversationKey])

  const sendMessage = useMemo(
    () => async (body: string) => {
      if (!channelScope || !channelId || !userId || !conversationKey) {
        return false
      }

      const trimmed = body.trim()
      if (!trimmed) {
        return false
      }
      if (trimmed.length > MAX_MESSAGE_BODY_LENGTH) {
        setConversationState((current) => current.key === conversationKey
          ? { ...current, error: `Message must be ${MAX_MESSAGE_BODY_LENGTH} characters or fewer` }
          : current)
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

      setConversationState((current) => current.key === conversationKey
        ? {
            ...current,
            isSending: true,
            error: null,
            messages: [...current.messages, optimisticMessage],
          }
        : current)

      try {
        if (!clientRef.current || clientKeyRef.current !== conversationKey) {
          throw new Error('Realtime connection is not ready')
        }
        clientRef.current.send(trimmed)
        return true
      } catch (caught) {
        setConversationState((current) => current.key === conversationKey
          ? {
              ...current,
              messages: current.messages.filter((message) => message.id !== optimisticId),
              error: caught instanceof Error ? caught.message : 'Unable to send message',
            }
          : current)
        return false
      } finally {
        setConversationState((current) => current.key === conversationKey
          ? { ...current, isSending: false }
          : current)
      }
    },
    [channelId, channelScope, conversationKey, userId],
  )

  return {
    messages: currentState.messages,
    connectionStatus: currentState.connectionStatus,
    isLoadingHistory: currentState.isLoadingHistory,
    error: currentState.error,
    sendMessage,
    isSending: currentState.isSending,
  }
}

function createConversationState(key: string | null, canConnect: boolean): ChannelConversationState {
  return {
    key,
    messages: [],
    connectionStatus: canConnect ? 'connecting' : 'disconnected',
    isLoadingHistory: canConnect,
    error: null,
    isSending: false,
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
  return merged.sort((left, right) => {
    const timeOrder = left.createdAt.localeCompare(right.createdAt)
    return timeOrder === 0 ? left.id.localeCompare(right.id) : timeOrder
  })
}

function isRealtimeConnectionError(message: string | null): boolean {
  return (
    message === 'Realtime connection failed' ||
    message === 'Received an invalid realtime event' ||
    message === 'Realtime connection is not ready'
  )
}
