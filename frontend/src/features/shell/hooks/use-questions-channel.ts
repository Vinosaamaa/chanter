import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { fetchPublicProfiles } from '../../friends/friends-api'
import type { PublicUserProfile } from '../../friends/types'
import {
  addSupportQuestionToTaQueue,
  fetchAssistantAnswer,
  listSupportQuestionReplies,
  listSupportQuestions,
  markAssistantAnswerHelpful,
  moderateSupportQuestion,
  postSupportQuestion,
  postSupportQuestionReply,
  quotaExhaustedMessage,
  streamAssistantAnswer,
} from '../../questions/questions-api'
import type {
  AssistantAnswer,
  AssistantStreamPhase,
  SupportQuestionModerationStatus,
  SupportQuestionReply,
  SupportQuestionSummary,
} from '../../questions/support-question-types'
import type { ChannelMessage } from '../channel-message-types'

type QuestionsChannelContext = {
  channelId: string
  cohortId: string
}

export type QuestionsTimelineEntry =
  | {
      kind: 'learner-question'
      message: ChannelMessage
      supportQuestion: SupportQuestionSummary
    }
  | {
      kind: 'ai-answer'
      supportQuestionId: string
      answer: AssistantAnswer
    }

type UseQuestionsChannelResult = {
  supportQuestions: SupportQuestionSummary[]
  timeline: QuestionsTimelineEntry[]
  profilesById: Record<string, PublicUserProfile>
  isLoadingHistory: boolean
  error: string | null
  postQuestion: (body: string) => Promise<boolean>
  isPosting: boolean
  invokeAssistant: (supportQuestionId: string) => Promise<void>
  invokingQuestionId: string | null
  streamingText: string
  streamPhase: AssistantStreamPhase
  markHelpful: (supportQuestionId: string) => Promise<void>
  markingHelpfulQuestionId: string | null
  addToTaQueue: (supportQuestionId: string) => Promise<void>
  addingToQueueQuestionId: string | null
  taQueueSuccess: string | null
  selectedSupportQuestionId: string | null
  selectSupportQuestion: (supportQuestionId: string | null) => void
  selectedQuestion: SupportQuestionSummary | null
  selectedAnswer: AssistantAnswer | null
  selectedReplies: SupportQuestionReply[]
  postReply: (supportQuestionId: string, body: string) => Promise<boolean>
  isPostingReply: boolean
  moderateQuestion: (
    supportQuestionId: string,
    status: SupportQuestionModerationStatus,
  ) => Promise<void>
  isModerating: boolean
  refresh: () => Promise<void>
}

const MAX_QUESTION_BODY_LENGTH = 4000

export function useQuestionsChannel({
  channelId,
  cohortId,
}: QuestionsChannelContext): UseQuestionsChannelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [supportQuestions, setSupportQuestions] = useState<SupportQuestionSummary[]>([])
  const [answersByQuestionId, setAnswersByQuestionId] = useState<Record<string, AssistantAnswer>>({})
  const [repliesByQuestionId, setRepliesByQuestionId] = useState<Record<string, SupportQuestionReply[]>>({})
  const [profilesById, setProfilesById] = useState<Record<string, PublicUserProfile>>({})
  const [loadedContextKey, setLoadedContextKey] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [isPosting, setIsPosting] = useState(false)
  const [isPostingReply, setIsPostingReply] = useState(false)
  const [isModerating, setIsModerating] = useState(false)
  const [invokingQuestionId, setInvokingQuestionId] = useState<string | null>(null)
  const [streamingText, setStreamingText] = useState('')
  const [streamPhase, setStreamPhase] = useState<AssistantStreamPhase>('idle')
  const [markingHelpfulQuestionId, setMarkingHelpfulQuestionId] = useState<string | null>(null)
  const streamAbortRef = useRef<AbortController | null>(null)
  const [addingToQueueQuestionId, setAddingToQueueQuestionId] = useState<string | null>(null)
  const [taQueueSuccess, setTaQueueSuccess] = useState<string | null>(null)
  const [selectedSupportQuestionId, setSelectedSupportQuestionId] = useState<string | null>(null)
  const postAttemptRef = useRef<{ body: string; key: string } | null>(null)
  const contextKey = channelId && userId ? `${channelId}:${userId}` : null
  const requestKey = contextKey ? `${contextKey}:${reloadToken}` : null
  const hasActiveData = contextKey !== null && loadedContextKey === contextKey

  useEffect(() => {
    if (!contextKey || !requestKey) return

    let cancelled = false

    void listSupportQuestions(channelId)
      .then((response) => {
        if (cancelled) return
        setError(null)
        setSupportQuestions(response.supportQuestions)
        setSelectedSupportQuestionId((current) => {
          if (current && response.supportQuestions.some((question) => question.id === current)) {
            return current
          }
          return response.supportQuestions.at(-1)?.id ?? null
        })
        setLoadedContextKey(contextKey)
      })
      .catch((caught) => {
        if (cancelled) return
        setSupportQuestions([])
        setError(caught instanceof Error ? caught.message : 'Unable to load Support Questions')
        setLoadedContextKey(contextKey)
      })

    return () => {
      cancelled = true
    }
  }, [channelId, contextKey, requestKey])

  const activeQuestions = useMemo(
    () => hasActiveData ? supportQuestions : [],
    [hasActiveData, supportQuestions],
  )
  const selectedQuestion = useMemo(
    () => activeQuestions.find((question) => question.id === selectedSupportQuestionId) ?? null,
    [activeQuestions, selectedSupportQuestionId],
  )

  useEffect(() => {
    if (!requestKey || !selectedQuestion) return

    let cancelled = false
    const answerRequest = fetchAssistantAnswer(channelId, selectedQuestion.id).catch((caught) => {
      if (caught instanceof ApiError && caught.status === 404) return null
      throw caught
    })

    void Promise.all([
      answerRequest,
      listSupportQuestionReplies(channelId, selectedQuestion.id),
    ])
      .then(([answer, replyList]) => {
        if (cancelled) return
        if (answer) {
          setAnswersByQuestionId((current) => ({ ...current, [selectedQuestion.id]: answer }))
        }
        setRepliesByQuestionId((current) => ({
          ...current,
          [selectedQuestion.id]: mergeReplies(
            replyList.replies,
            current[selectedQuestion.id] ?? [],
          ),
        }))
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'Unable to load the question thread')
        }
      })

    return () => {
      cancelled = true
    }
  }, [channelId, requestKey, selectedQuestion])

  const selectedReplies = useMemo(
    () => selectedSupportQuestionId
      ? repliesByQuestionId[selectedSupportQuestionId] ?? []
      : [],
    [repliesByQuestionId, selectedSupportQuestionId],
  )
  const profileUserIds = useMemo(
    () => Array.from(new Set([
      ...activeQuestions.map((question) => question.senderUserId),
      ...selectedReplies.map((reply) => reply.authorUserId),
    ])).filter((id) => !profilesById[id]),
    [activeQuestions, profilesById, selectedReplies],
  )
  const profileRequestKey = profileUserIds.slice().sort().join(':')

  useEffect(() => {
    if (!profileRequestKey) return
    let cancelled = false
    void fetchPublicProfiles(profileUserIds).then((response) => {
      if (cancelled) return
      setProfilesById((current) => ({
        ...current,
        ...Object.fromEntries(response.profiles.map((profile) => [profile.userId, profile])),
      }))
    }).catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [profileRequestKey, profileUserIds])

  const timeline = useMemo<QuestionsTimelineEntry[]>(() => {
    const entries: QuestionsTimelineEntry[] = activeQuestions.map((question) => ({
      kind: 'learner-question',
      message: {
        id: question.channelMessageId,
        channelId: question.channelId,
        senderUserId: question.senderUserId,
        body: question.body,
        createdAt: question.createdAt,
      },
      supportQuestion: question,
    }))
    for (const answer of Object.values(answersByQuestionId)) {
      if (activeQuestions.some((question) => question.id === answer.supportQuestionId)) {
        entries.push({ kind: 'ai-answer', supportQuestionId: answer.supportQuestionId, answer })
      }
    }
    return entries.sort((left, right) => entryTimestamp(left).localeCompare(entryTimestamp(right)))
  }, [activeQuestions, answersByQuestionId])

  const postQuestion = useCallback(async (body: string) => {
    if (!userId) return false
    const trimmed = body.trim()
    if (!trimmed) return false
    if (trimmed.length > MAX_QUESTION_BODY_LENGTH) {
      setError(`Question must be ${MAX_QUESTION_BODY_LENGTH} characters or fewer`)
      return false
    }

    const previousAttempt = postAttemptRef.current
    const idempotencyKey = previousAttempt?.body === trimmed ? previousAttempt.key : crypto.randomUUID()
    postAttemptRef.current = { body: trimmed, key: idempotencyKey }
    setIsPosting(true)
    setError(null)
    setTaQueueSuccess(null)
    try {
      const created = await postSupportQuestion(channelId, trimmed, idempotencyKey)
      postAttemptRef.current = null
      setSupportQuestions((current) => [...current, created])
      setSelectedSupportQuestionId(created.id)
      return true
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to post Support Question')
      return false
    } finally {
      setIsPosting(false)
    }
  }, [channelId, userId])

  const invokeAssistant = useCallback(async (supportQuestionId: string) => {
    streamAbortRef.current?.abort()
    const abortController = new AbortController()
    streamAbortRef.current = abortController

    setInvokingQuestionId(supportQuestionId)
    setStreamingText('')
    setStreamPhase('streaming')
    setError(null)
    setTaQueueSuccess(null)
    setSelectedSupportQuestionId(supportQuestionId)
    try {
      await streamAssistantAnswer(channelId, supportQuestionId, {
        signal: abortController.signal,
        onToken: (token) => {
          setStreamingText((current) => current + token)
        },
        onComplete: (answer) => {
          setAnswersByQuestionId((current) => ({ ...current, [supportQuestionId]: answer }))
          setSupportQuestions((current) => current.map((question) =>
            question.id === supportQuestionId
              ? { ...question, status: answer.supportQuestionStatus }
              : question,
          ))
          setStreamingText('')
          setStreamPhase('complete')
        },
      })
    } catch (caught) {
      if (abortController.signal.aborted) {
        setStreamPhase('idle')
        return
      }
      setStreamPhase('error')
      if (caught instanceof ApiError && caught.status === 429) {
        setError(quotaExhaustedMessage(caught.body))
      } else {
        setError(caught instanceof Error ? caught.message : 'Unable to invoke AI Study Assistant')
      }
    } finally {
      if (streamAbortRef.current === abortController) {
        streamAbortRef.current = null
      }
      setInvokingQuestionId(null)
      if (!abortController.signal.aborted) {
        setStreamingText((current) => {
          if (current.length > 0) {
            return ''
          }
          return current
        })
      }
    }
  }, [channelId])

  const markHelpful = useCallback(async (supportQuestionId: string) => {
    setMarkingHelpfulQuestionId(supportQuestionId)
    setError(null)
    try {
      const updated = await markAssistantAnswerHelpful(channelId, supportQuestionId)
      setAnswersByQuestionId((current) => ({ ...current, [supportQuestionId]: updated }))
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to mark answer helpful')
    } finally {
      setMarkingHelpfulQuestionId(null)
    }
  }, [channelId])

  const selectSupportQuestion = useCallback((supportQuestionId: string | null) => {
    if (supportQuestionId !== selectedSupportQuestionId) {
      streamAbortRef.current?.abort()
      streamAbortRef.current = null
      setStreamingText('')
      setStreamPhase('idle')
    }
    setSelectedSupportQuestionId(supportQuestionId)
  }, [selectedSupportQuestionId])

  const addToTaQueue = useCallback(async (supportQuestionId: string) => {
    const answer = answersByQuestionId[supportQuestionId]
    if (!answer?.handoffRecommended || !cohortId) return
    setAddingToQueueQuestionId(supportQuestionId)
    setError(null)
    setTaQueueSuccess(null)
    try {
      await addSupportQuestionToTaQueue(cohortId, supportQuestionId, channelId)
      setTaQueueSuccess('Added to TA Queue. A teaching assistant will follow up soon.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to add to TA Queue')
    } finally {
      setAddingToQueueQuestionId(null)
    }
  }, [answersByQuestionId, channelId, cohortId])

  const postReply = useCallback(async (supportQuestionId: string, body: string) => {
    const trimmed = body.trim()
    if (!trimmed) return false
    setIsPostingReply(true)
    setError(null)
    try {
      const created = await postSupportQuestionReply(channelId, supportQuestionId, trimmed)
      setRepliesByQuestionId((current) => ({
        ...current,
        [supportQuestionId]: [...(current[supportQuestionId] ?? []), created],
      }))
      setSupportQuestions((current) => current.map((question) =>
        question.id === supportQuestionId ? { ...question, status: 'HUMAN_ANSWERED' } : question,
      ))
      return true
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to post reply')
      return false
    } finally {
      setIsPostingReply(false)
    }
  }, [channelId])

  const moderateQuestion = useCallback(async (
    supportQuestionId: string,
    status: SupportQuestionModerationStatus,
  ) => {
    setIsModerating(true)
    setError(null)
    try {
      const updated = await moderateSupportQuestion(channelId, supportQuestionId, status)
      setSupportQuestions((current) => current.map((question) =>
        question.id === supportQuestionId ? updated : question,
      ))
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to update Support Question')
    } finally {
      setIsModerating(false)
    }
  }, [channelId])

  const refresh = useCallback(async () => {
    setReloadToken((current) => current + 1)
  }, [])

  return {
    supportQuestions: activeQuestions,
    timeline,
    profilesById: hasActiveData ? profilesById : {},
    isLoadingHistory: Boolean(contextKey && !hasActiveData),
    error,
    postQuestion,
    isPosting,
    invokeAssistant,
    invokingQuestionId,
    streamingText,
    streamPhase,
    markHelpful,
    markingHelpfulQuestionId,
    addToTaQueue,
    addingToQueueQuestionId,
    taQueueSuccess,
    selectedSupportQuestionId,
    selectSupportQuestion,
    selectedQuestion,
    selectedAnswer: selectedSupportQuestionId
      ? answersByQuestionId[selectedSupportQuestionId] ?? null
      : null,
    selectedReplies,
    postReply,
    isPostingReply,
    moderateQuestion,
    isModerating,
    refresh,
  }
}

function entryTimestamp(entry: QuestionsTimelineEntry): string {
  return entry.kind === 'learner-question' ? entry.message.createdAt : entry.answer.createdAt
}

function mergeReplies(
  fetched: SupportQuestionReply[],
  current: SupportQuestionReply[],
): SupportQuestionReply[] {
  const byId = new Map(fetched.map((reply) => [reply.id, reply]))
  current.forEach((reply) => byId.set(reply.id, reply))
  return [...byId.values()].sort((left, right) => left.createdAt.localeCompare(right.createdAt))
}
