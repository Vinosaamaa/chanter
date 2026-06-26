import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import type { ChannelMessage } from '../channel-message-types'
import { fetchChannelMessages } from '../channel-messages-api'
import {
  addSupportQuestionToTaQueue,
  invokeAssistantAnswer,
  listUnansweredSupportQuestions,
  postSupportQuestion,
  quotaExhaustedMessage,
} from '../../questions/questions-api'
import type { AssistantAnswer, SupportQuestion } from '../../questions/support-question-types'

type QuestionsChannelContext = {
  channelId: string
  cohortId: string
}

export type QuestionsTimelineEntry =
  | {
      kind: 'learner-question'
      message: ChannelMessage
      supportQuestion: SupportQuestion | null
    }
  | {
      kind: 'ai-answer'
      supportQuestionId: string
      answer: AssistantAnswer
    }

type UseQuestionsChannelResult = {
  timeline: QuestionsTimelineEntry[]
  isLoadingHistory: boolean
  error: string | null
  postQuestion: (body: string) => Promise<boolean>
  isPosting: boolean
  invokeAssistant: (supportQuestionId: string) => Promise<void>
  invokingQuestionId: string | null
  addToTaQueue: (supportQuestionId: string) => Promise<void>
  addingToQueueQuestionId: string | null
  taQueueSuccess: string | null
  selectedSupportQuestionId: string | null
  selectSupportQuestion: (supportQuestionId: string | null) => void
  selectedAnswer: AssistantAnswer | null
}

const MAX_QUESTION_BODY_LENGTH = 4000

export function useQuestionsChannel({
  channelId,
  cohortId,
}: QuestionsChannelContext): UseQuestionsChannelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [messages, setMessages] = useState<ChannelMessage[]>([])
  const [supportQuestionsById, setSupportQuestionsById] = useState<Record<string, SupportQuestion>>({})
  const [answersByQuestionId, setAnswersByQuestionId] = useState<Record<string, AssistantAnswer>>({})
  const [loadedHistoryKey, setLoadedHistoryKey] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isPosting, setIsPosting] = useState(false)
  const [invokingQuestionId, setInvokingQuestionId] = useState<string | null>(null)
  const [addingToQueueQuestionId, setAddingToQueueQuestionId] = useState<string | null>(null)
  const [taQueueSuccess, setTaQueueSuccess] = useState<string | null>(null)
  const [selectedSupportQuestionId, setSelectedSupportQuestionId] = useState<string | null>(null)
  const postAttemptRef = useRef<{ body: string; key: string } | null>(null)
  const historyRequestKey = channelId && userId ? `${channelId}:${userId}` : null
  const isLoadingHistory = historyRequestKey !== null && loadedHistoryKey !== historyRequestKey

  useEffect(() => {
    if (!historyRequestKey || !channelId || !userId) {
      return
    }

    let cancelled = false

    void Promise.all([
      fetchChannelMessages('course', channelId),
      listUnansweredSupportQuestions(channelId),
    ])
      .then(([messageResponse, supportQuestionResponse]) => {
        if (cancelled) {
          return
        }

        setMessages(messageResponse.messages)
        setSupportQuestionsById((current) => {
          const next = { ...current }
          for (const summary of supportQuestionResponse.supportQuestions) {
            next[summary.id] = {
              id: summary.id,
              channelMessageId: summary.channelMessageId,
              channelId: summary.channelId,
              senderUserId: summary.senderUserId,
              body: summary.body,
              status: summary.status,
              idempotencyKey: '',
              createdAt: summary.createdAt,
            }
          }
          return next
        })
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'Unable to load #questions history')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadedHistoryKey(historyRequestKey)
        }
      })

    return () => {
      cancelled = true
    }
  }, [channelId, historyRequestKey, userId])

  const supportQuestionByMessageId = useMemo(() => {
    const map = new Map<string, SupportQuestion>()
    for (const question of Object.values(supportQuestionsById)) {
      if (question.channelMessageId) {
        map.set(question.channelMessageId, question)
      }
    }
    return map
  }, [supportQuestionsById])

  const timeline = useMemo(() => {
    const entries: QuestionsTimelineEntry[] = []

    for (const message of messages) {
      entries.push({
        kind: 'learner-question',
        message,
        supportQuestion: supportQuestionByMessageId.get(message.id) ?? null,
      })
    }

    for (const answer of Object.values(answersByQuestionId)) {
      entries.push({
        kind: 'ai-answer',
        supportQuestionId: answer.supportQuestionId,
        answer,
      })
    }

    return entries.sort((left, right) => {
      const leftTime = entryTimestamp(left)
      const rightTime = entryTimestamp(right)
      return leftTime.localeCompare(rightTime)
    })
  }, [answersByQuestionId, messages, supportQuestionByMessageId])

  const selectedAnswer = useMemo(() => {
    if (!selectedSupportQuestionId) {
      const lastAnswer = Object.values(answersByQuestionId).at(-1)
      return lastAnswer ?? null
    }
    return answersByQuestionId[selectedSupportQuestionId] ?? null
  }, [answersByQuestionId, selectedSupportQuestionId])

  const postQuestion = useCallback(
    async (body: string) => {
      if (!userId) {
        return false
      }

      const trimmed = body.trim()
      if (!trimmed) {
        return false
      }
      if (trimmed.length > MAX_QUESTION_BODY_LENGTH) {
        setError(`Question must be ${MAX_QUESTION_BODY_LENGTH} characters or fewer`)
        return false
      }

      const optimisticMessageId = `optimistic-${crypto.randomUUID()}`
      const optimisticMessage: ChannelMessage = {
        id: optimisticMessageId,
        channelId,
        senderUserId: userId,
        body: trimmed,
        createdAt: new Date().toISOString(),
      }

      setIsPosting(true)
      setError(null)
      setTaQueueSuccess(null)
      setMessages((current) => [...current, optimisticMessage])

      const previousAttempt = postAttemptRef.current
      const idempotencyKey =
        previousAttempt?.body === trimmed ? previousAttempt.key : crypto.randomUUID()
      postAttemptRef.current = { body: trimmed, key: idempotencyKey }

      try {
        const created = await postSupportQuestion(channelId, trimmed, idempotencyKey)
        postAttemptRef.current = null
        setSupportQuestionsById((current) => ({ ...current, [created.id]: created }))
        setSelectedSupportQuestionId(created.id)
        setMessages((current) =>
          current.map((message) =>
            message.id === optimisticMessageId
              ? {
                  id: created.channelMessageId,
                  channelId: created.channelId,
                  senderUserId: created.senderUserId,
                  body: created.body,
                  createdAt: created.createdAt,
                }
              : message,
          ),
        )
        return true
      } catch (caught) {
        setMessages((current) => current.filter((message) => message.id !== optimisticMessageId))
        setError(caught instanceof Error ? caught.message : 'Unable to post Support Question')
        return false
      } finally {
        setIsPosting(false)
      }
    },
    [channelId, userId],
  )

  const invokeAssistant = useCallback(
    async (supportQuestionId: string) => {
      if (!userId) {
        return
      }

      setInvokingQuestionId(supportQuestionId)
      setError(null)
      setTaQueueSuccess(null)
      setSelectedSupportQuestionId(supportQuestionId)

      try {
        const answer = await invokeAssistantAnswer(channelId, supportQuestionId, userId)
        setAnswersByQuestionId((current) => ({ ...current, [supportQuestionId]: answer }))
        setSupportQuestionsById((current) => {
          const existing = current[supportQuestionId]
          if (!existing) {
            return current
          }
          return {
            ...current,
            [supportQuestionId]: {
              ...existing,
              status: answer.supportQuestionStatus,
            },
          }
        })
      } catch (caught) {
        if (caught instanceof ApiError && caught.status === 429) {
          setError(quotaExhaustedMessage(caught.body))
        } else {
          setError(caught instanceof Error ? caught.message : 'Unable to invoke AI Study Assistant')
        }
      } finally {
        setInvokingQuestionId(null)
      }
    },
    [channelId, userId],
  )

  const addToTaQueue = useCallback(
    async (supportQuestionId: string) => {
      if (!userId) {
        return
      }

      const answer = answersByQuestionId[supportQuestionId]
      if (!answer?.handoffRecommended) {
        return
      }

      setAddingToQueueQuestionId(supportQuestionId)
      setError(null)
      setTaQueueSuccess(null)

      try {
        await addSupportQuestionToTaQueue(cohortId, userId, supportQuestionId, channelId)
        setTaQueueSuccess('Added to TA Queue. A teaching assistant will follow up soon.')
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : 'Unable to add to TA Queue')
      } finally {
        setAddingToQueueQuestionId(null)
      }
    },
    [answersByQuestionId, channelId, cohortId, userId],
  )

  return {
    timeline,
    isLoadingHistory,
    error,
    postQuestion,
    isPosting,
    invokeAssistant,
    invokingQuestionId,
    addToTaQueue,
    addingToQueueQuestionId,
    taQueueSuccess,
    selectedSupportQuestionId,
    selectSupportQuestion: setSelectedSupportQuestionId,
    selectedAnswer,
  }
}

function entryTimestamp(entry: QuestionsTimelineEntry): string {
  if (entry.kind === 'learner-question') {
    return entry.message.createdAt
  }
  return entry.answer.createdAt
}
