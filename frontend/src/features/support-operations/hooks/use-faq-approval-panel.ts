import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { listApprovedFaqs, listFaqCandidates, upsertApprovedFaq } from '../faq-api'
import type { ApprovedFaq, FaqCandidateGroup } from '../support-operations-types'

type UseFaqApprovalPanelResult = {
  candidates: FaqCandidateGroup[]
  approvedFaqs: ApprovedFaq[]
  selectedIndex: number
  questionDraft: string
  setQuestionDraft: (value: string) => void
  answerDraft: string
  setAnswerDraft: (value: string) => void
  editingFaqId: string | null
  isLoading: boolean
  accessDenied: boolean
  error: string | null
  actionMessage: string | null
  isSaving: boolean
  refresh: () => Promise<void>
  selectCandidate: (index: number) => void
  startEditApproved: (faq: ApprovedFaq) => void
  clearEdit: () => void
  approveOrUpdate: () => Promise<void>
}

function accessErrorMessage(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 403) {
      return 'Only course instructors can approve FAQs.'
    }
    if (caught.body && caught.body.trim().length > 0) {
      return caught.body
    }
    return `Request failed (${caught.status}).`
  }
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unable to load FAQ candidates.'
}

export function useFaqApprovalPanel(
  courseId: string | undefined,
  questionsChannelId: string | undefined,
): UseFaqApprovalPanelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [candidates, setCandidates] = useState<FaqCandidateGroup[]>([])
  const [approvedFaqs, setApprovedFaqs] = useState<ApprovedFaq[]>([])
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [questionDraft, setQuestionDraft] = useState('')
  const [answerDraft, setAnswerDraft] = useState('')
  const [editingFaqId, setEditingFaqId] = useState<string | null>(null)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [sourceIdsByFaqId, setSourceIdsByFaqId] = useState<Record<string, string[]>>({})
  const [reloadToken, setReloadToken] = useState(0)

  const requestKey =
    courseId && questionsChannelId && userId
      ? `${courseId}:${questionsChannelId}:${userId}:${reloadToken}`
      : null
  const isLoading = requestKey !== null && loadedKey !== requestKey

  useEffect(() => {
    if (!courseId || !questionsChannelId || !userId || requestKey === null) {
      return
    }

    let cancelled = false

    void (async () => {
      setAccessDenied(false)
      setError(null)
      setActionMessage(null)

      try {
        const candidateList = await listFaqCandidates(questionsChannelId, userId)
        if (cancelled) {
          return
        }

        setCandidates(candidateList.faqCandidates)
        setSelectedIndex(0)
        setEditingFaqId(null)
        setAnswerDraft('')

        if (candidateList.faqCandidates.length > 0) {
          setQuestionDraft(candidateList.faqCandidates[0].representativeQuestion)
        } else {
          setQuestionDraft('')
        }
      } catch (caught) {
        if (cancelled) {
          return
        }

        if (caught instanceof ApiError && caught.status === 403) {
          setAccessDenied(true)
        }
        setError(accessErrorMessage(caught))
      }

      try {
        const approvedList = await listApprovedFaqs(courseId, userId)
        if (!cancelled) {
          setApprovedFaqs(approvedList.approvedFaqs)
        }
      } catch {
        if (!cancelled) {
          setApprovedFaqs([])
        }
      }

      if (!cancelled) {
        setLoadedKey(requestKey)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [courseId, questionsChannelId, requestKey, userId])

  const refresh = useCallback(async () => {
    if (isSaving) {
      return
    }

    setReloadToken((current) => current + 1)
  }, [isSaving])

  const selectCandidate = useCallback(
    (index: number) => {
      if (isSaving) {
        return
      }

      setSelectedIndex(index)
      setEditingFaqId(null)
      setAnswerDraft('')
      setQuestionDraft(candidates[index]?.representativeQuestion ?? '')
    },
    [candidates, isSaving],
  )

  const startEditApproved = useCallback((faq: ApprovedFaq) => {
    if (isSaving) {
      return
    }

    setEditingFaqId(faq.id)
    setQuestionDraft(faq.question)
    setAnswerDraft(faq.answer)
    setActionMessage(null)
    setError(null)
  }, [isSaving])

  const clearEdit = useCallback(() => {
    if (isSaving) {
      return
    }

    setEditingFaqId(null)
    setAnswerDraft('')
    setQuestionDraft(candidates[selectedIndex]?.representativeQuestion ?? '')
  }, [candidates, isSaving, selectedIndex])

  const resolveSourceSupportQuestionIds = useCallback((): string[] => {
    const candidate = candidates[selectedIndex]
    if (editingFaqId) {
      const stored = sourceIdsByFaqId[editingFaqId]
      if (stored && stored.length > 0) {
        return stored
      }

      const editingFaq = approvedFaqs.find((faq) => faq.id === editingFaqId)
      const matchingCandidate = candidates.find(
        (group) => group.representativeQuestion === editingFaq?.question,
      )
      if (matchingCandidate) {
        return matchingCandidate.supportQuestions.map((question) => question.id)
      }

      return candidate?.supportQuestions.map((question) => question.id) ?? []
    }

    return candidate?.supportQuestions.map((question) => question.id) ?? []
  }, [approvedFaqs, candidates, editingFaqId, selectedIndex, sourceIdsByFaqId])

  const approveOrUpdate = useCallback(async () => {
    if (!courseId || !questionsChannelId || !userId) {
      return
    }

    const trimmedQuestion = questionDraft.trim()
    const trimmedAnswer = answerDraft.trim()
    if (!trimmedQuestion || !trimmedAnswer) {
      setError('Question and answer are required.')
      return
    }

    const sourceSupportQuestionIds = resolveSourceSupportQuestionIds()
    if (sourceSupportQuestionIds.length === 0) {
      setError('Approved FAQ requires at least one linked support question.')
      return
    }

    setIsSaving(true)
    setError(null)
    setActionMessage(null)

    try {
      const saved = await upsertApprovedFaq(courseId, {
        channelId: questionsChannelId,
        approvedByUserId: userId,
        id: editingFaqId ?? undefined,
        question: trimmedQuestion,
        answer: trimmedAnswer,
        sourceSupportQuestionIds,
      })

      setSourceIdsByFaqId((current) => ({
        ...current,
        [saved.id]: sourceSupportQuestionIds,
      }))

      setApprovedFaqs((current) => {
        if (current.some((faq) => faq.id === saved.id)) {
          return current.map((faq) => (faq.id === saved.id ? saved : faq))
        }
        return [saved, ...current]
      })

      if (editingFaqId) {
        setActionMessage('Updated approved FAQ.')
      } else {
        const approvedSourceIds = new Set(sourceSupportQuestionIds)
        const nextCandidates = candidates.filter(
          (group) => !group.supportQuestions.some((question) => approvedSourceIds.has(question.id)),
        )
        setCandidates(nextCandidates)
        setSelectedIndex(0)
        setQuestionDraft(nextCandidates[0]?.representativeQuestion ?? '')
        setActionMessage('Approved FAQ for this course.')
        setAnswerDraft('')
      }

      setEditingFaqId(null)
    } catch (caught) {
      setError(accessErrorMessage(caught))
    } finally {
      setIsSaving(false)
    }
  }, [
    answerDraft,
    candidates,
    courseId,
    editingFaqId,
    questionDraft,
    questionsChannelId,
    resolveSourceSupportQuestionIds,
    userId,
  ])

  return {
    candidates,
    approvedFaqs,
    selectedIndex,
    questionDraft,
    setQuestionDraft,
    answerDraft,
    setAnswerDraft,
    editingFaqId,
    isLoading,
    accessDenied,
    error,
    actionMessage,
    isSaving,
    refresh,
    selectCandidate,
    startEditApproved,
    clearEdit,
    approveOrUpdate,
  }
}
