import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { useQuestionsChannel } from './use-questions-channel'

const mocks = vi.hoisted(() => ({
  fetchAssistantAnswer: vi.fn(),
  fetchPublicProfiles: vi.fn(),
  listSupportQuestionReplies: vi.fn(),
  listSupportQuestions: vi.fn(),
  postSupportQuestionReply: vi.fn(),
  streamAssistantAnswer: vi.fn(),
  markAssistantAnswerHelpful: vi.fn(),
}))

vi.mock('../../friends/friends-api', () => ({
  fetchPublicProfiles: mocks.fetchPublicProfiles,
}))

vi.mock('../../questions/questions-api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../questions/questions-api')>(),
  fetchAssistantAnswer: mocks.fetchAssistantAnswer,
  listSupportQuestionReplies: mocks.listSupportQuestionReplies,
  listSupportQuestions: mocks.listSupportQuestions,
  postSupportQuestionReply: mocks.postSupportQuestionReply,
  streamAssistantAnswer: mocks.streamAssistantAnswer,
  markAssistantAnswerHelpful: mocks.markAssistantAnswerHelpful,
}))

describe('useQuestionsChannel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: {
        id: 'learner-1',
        email: 'learner@example.com',
        displayName: 'Learner Lin',
      },
    })
    mocks.listSupportQuestions.mockResolvedValue({
      supportQuestions: [{
        id: 'question-1',
        channelMessageId: 'message-1',
        channelId: 'questions-1',
        senderUserId: 'learner-1',
        body: 'What is the deadline?',
        status: 'UNANSWERED',
        createdAt: '2026-07-14T20:00:00.000Z',
      }],
    })
    mocks.listSupportQuestionReplies.mockResolvedValue({ replies: [] })
    mocks.fetchPublicProfiles.mockImplementation((userIds: string[]) => Promise.resolve({
      profiles: userIds.map((userId) => ({ userId, displayName: userId })),
    }))
    mocks.fetchAssistantAnswer.mockRejectedValue(new Error('not found'))
  })

  it('reloads a persisted assistant answer after the question is resolved', async () => {
    mocks.listSupportQuestions.mockResolvedValue({
      supportQuestions: [{
        id: 'question-1',
        channelMessageId: 'message-1',
        channelId: 'questions-1',
        senderUserId: 'learner-1',
        body: 'What is the deadline?',
        status: 'RESOLVED',
        createdAt: '2026-07-14T20:00:00.000Z',
      }],
    })
    mocks.fetchAssistantAnswer.mockResolvedValue({
      id: 'answer-1',
      supportQuestionId: 'question-1',
      answerBody: 'The deadline is Friday.',
      supportQuestionStatus: 'AI_ANSWERED',
      handoffRecommended: false,
      sources: [],
      createdAt: '2026-07-14T20:01:00.000Z',
    })

    const { result } = renderHook(() => useQuestionsChannel({
      channelId: 'questions-1',
      cohortId: 'cohort-1',
    }))

    await waitFor(() => {
      expect(result.current.selectedQuestion?.id).toBe('question-1')
    })
    await waitFor(() => {
      expect(mocks.fetchAssistantAnswer).toHaveBeenCalledWith('questions-1', 'question-1')
      expect(result.current.selectedAnswer?.id).toBe('answer-1')
    })
  })

  it('streams tokens then stores the complete answer', async () => {
    mocks.streamAssistantAnswer.mockImplementation(async (_channelId, _questionId, handlers) => {
      handlers.onToken('Hello ')
      handlers.onToken('world')
      handlers.onComplete({
        id: 'answer-stream',
        supportQuestionId: 'question-1',
        channelId: 'questions-1',
        studyServerId: 'server-1',
        learnerUserId: 'learner-1',
        questionBody: 'What is the deadline?',
        answerBody: 'Hello world',
        confidence: 'HIGH',
        handoffRecommended: false,
        supportQuestionStatus: 'AI_ANSWERED',
        sources: [{ resourceId: 'resource-1', resourceTitle: 'Guide', excerpt: 'deadline Friday' }],
        createdAt: '2026-07-14T20:01:00.000Z',
        audit: {
          invocationType: 'GROUNDED_ANSWER',
          sourceCount: 1,
          llmUsed: false,
          createdAt: '2026-07-14T20:01:00.000Z',
        },
        helpfulMarked: false,
        helpfulCount: 0,
      })
    })

    const { result } = renderHook(() => useQuestionsChannel({
      channelId: 'questions-1',
      cohortId: 'cohort-1',
    }))

    await waitFor(() => {
      expect(result.current.selectedQuestion?.id).toBe('question-1')
    })

    await act(async () => {
      await result.current.invokeAssistant('question-1')
    })

    expect(mocks.streamAssistantAnswer).toHaveBeenCalled()
    expect(result.current.streamPhase).toBe('complete')
    expect(result.current.streamingText).toBe('')
    expect(result.current.selectedAnswer?.answerBody).toBe('Hello world')
    expect(result.current.selectedAnswer?.sources).toHaveLength(1)
    expect(result.current.selectedQuestion?.status).toBe('AI_ANSWERED')
  })

  it('marks an answer helpful', async () => {
    mocks.fetchAssistantAnswer.mockResolvedValue({
      id: 'answer-1',
      supportQuestionId: 'question-1',
      answerBody: 'The deadline is Friday.',
      supportQuestionStatus: 'AI_ANSWERED',
      handoffRecommended: false,
      sources: [],
      createdAt: '2026-07-14T20:01:00.000Z',
      helpfulMarked: false,
      helpfulCount: 0,
    })
    mocks.listSupportQuestions.mockResolvedValue({
      supportQuestions: [{
        id: 'question-1',
        channelMessageId: 'message-1',
        channelId: 'questions-1',
        senderUserId: 'learner-1',
        body: 'What is the deadline?',
        status: 'AI_ANSWERED',
        createdAt: '2026-07-14T20:00:00.000Z',
      }],
    })
    mocks.markAssistantAnswerHelpful.mockResolvedValue({
      id: 'answer-1',
      supportQuestionId: 'question-1',
      answerBody: 'The deadline is Friday.',
      supportQuestionStatus: 'AI_ANSWERED',
      handoffRecommended: false,
      sources: [],
      createdAt: '2026-07-14T20:01:00.000Z',
      helpfulMarked: true,
      helpfulCount: 1,
    })

    const { result } = renderHook(() => useQuestionsChannel({
      channelId: 'questions-1',
      cohortId: 'cohort-1',
    }))

    await waitFor(() => {
      expect(result.current.selectedAnswer?.id).toBe('answer-1')
    })

    await act(async () => {
      await result.current.markHelpful('question-1')
    })

    expect(result.current.selectedAnswer?.helpfulMarked).toBe(true)
    expect(result.current.selectedAnswer?.helpfulCount).toBe(1)
  })

  it('keeps a newly posted reply when an older thread request completes later', async () => {
    let resolveReplyList: ((value: { replies: never[] }) => void) | undefined
    mocks.listSupportQuestionReplies.mockReturnValue(new Promise((resolve) => {
      resolveReplyList = resolve
    }))
    mocks.postSupportQuestionReply.mockResolvedValue({
      id: 'reply-new',
      supportQuestionId: 'question-1',
      authorUserId: 'instructor-1',
      body: 'A durable staff reply.',
      createdAt: '2026-07-14T20:02:00.000Z',
    })

    const { result } = renderHook(() => useQuestionsChannel({
      channelId: 'questions-1',
      cohortId: 'cohort-1',
    }))

    await waitFor(() => {
      expect(result.current.selectedQuestion?.id).toBe('question-1')
    })
    await act(async () => {
      await result.current.postReply('question-1', 'A durable staff reply.')
    })
    expect(result.current.selectedReplies).toHaveLength(1)

    await act(async () => {
      resolveReplyList?.({ replies: [] })
    })

    expect(result.current.selectedReplies).toHaveLength(1)
    expect(result.current.selectedReplies[0]?.id).toBe('reply-new')
  })
})
