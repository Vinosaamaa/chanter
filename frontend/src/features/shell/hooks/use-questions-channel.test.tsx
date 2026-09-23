import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AssistantStreamError, type StreamAssistantHandlers } from '../../questions/questions-api'
import { useAuthStore } from '../../../stores/auth-store'
import { ApiError } from '../../../lib/api-client'
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
    mocks.fetchAssistantAnswer.mockRejectedValue(new ApiError('not found', 404))
  })

  const savedAnswer = {
    id: 'answer-retained', supportQuestionId: 'question-1', answerBody: 'Removed source quotation',
    supportQuestionStatus: 'AI_ANSWERED', handoffRecommended: false, sources: [],
    createdAt: '2026-07-14T20:01:00.000Z', helpfulMarked: false, helpfulCount: 0,
  }
  const savedReply = {
    id: 'reply-retained', supportQuestionId: 'question-1', authorUserId: 'instructor-1',
    body: 'Removed author reply', createdAt: '2026-07-14T20:02:00.000Z',
  }

  it.each([false, true])('removes a cached answer after authoritative 404, even when replies fail: %s', async (repliesFail) => {
    mocks.fetchAssistantAnswer.mockResolvedValue(savedAnswer)
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedAnswer?.id).toBe(savedAnswer.id))
    mocks.fetchAssistantAnswer.mockRejectedValue(new ApiError('not found', 404))
    if (repliesFail) mocks.listSupportQuestionReplies.mockRejectedValue(new ApiError('Unavailable', 500))
    await act(() => result.current.refresh())
    await waitFor(() => expect(result.current.selectedAnswer).toBeNull())
    expect(result.current.timeline.some(entry => entry.kind === 'ai-answer')).toBe(false)
    if (repliesFail) expect(result.current.error).toBe('Unavailable')
  })

  it.each([false, true])('removes a cached reply absent from the current list, even when the answer fails: %s', async (answerFails) => {
    mocks.listSupportQuestionReplies.mockResolvedValue({ replies: [savedReply] })
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedReplies).toHaveLength(1))
    mocks.listSupportQuestionReplies.mockResolvedValue({ replies: [] })
    if (answerFails) mocks.fetchAssistantAnswer.mockRejectedValue(new ApiError('Unavailable', 500))
    await act(() => result.current.refresh())
    await waitFor(() => expect(result.current.selectedReplies).toEqual([]))
    if (answerFails) expect(result.current.error).toBe('Unavailable')
  })

  it.each([false, true])('preserves a later helpful result against an older read, then accepts current deletion: %s', async (missing) => {
    mocks.fetchAssistantAnswer.mockResolvedValue(savedAnswer)
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedAnswer?.id).toBe(savedAnswer.id))
    let finish!: () => void
    mocks.fetchAssistantAnswer.mockImplementationOnce(() => new Promise((resolve, reject) => {
      finish = () => missing ? reject(new ApiError('not found', 404)) : resolve(savedAnswer)
    }))
    await act(() => result.current.refresh())
    await waitFor(() => expect(mocks.fetchAssistantAnswer).toHaveBeenCalledTimes(2))
    mocks.markAssistantAnswerHelpful.mockResolvedValue({ ...savedAnswer, helpfulMarked: true, helpfulCount: 1 })
    await act(() => result.current.markHelpful('question-1'))
    await act(async () => finish())
    expect(result.current.selectedAnswer?.helpfulMarked).toBe(true)
    mocks.fetchAssistantAnswer.mockRejectedValue(new ApiError('not found', 404))
    await act(() => result.current.refresh())
    await waitFor(() => expect(result.current.selectedAnswer).toBeNull())
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

  it('preserves explicit new-question intent when initial history arrives', async () => {
    const history = await mocks.listSupportQuestions()
    let finish!: (value: typeof history) => void
    mocks.listSupportQuestions.mockReturnValue(new Promise(resolve => { finish = resolve }))
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    act(() => result.current.selectSupportQuestion(null))
    await act(async () => finish(history))
    expect(result.current.supportQuestions).toHaveLength(1)
    expect(result.current.selectedSupportQuestionId).toBeNull()
    expect(result.current.selectedQuestion).toBeNull()
  })

  it('does not redirect an existing selection when refresh omits its question', async () => {
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
    const original = result.current.selectedQuestion!
    mocks.listSupportQuestions.mockResolvedValue({ supportQuestions: [{ ...original, id: 'question-2' }] })
    await act(async () => result.current.refresh())
    await waitFor(() => expect(result.current.supportQuestions[0]?.id).toBe('question-2'))
    expect(result.current.selectedQuestion).toBeNull()
    expect(result.current.selectedSupportQuestionId).toBe('question-1')
    mocks.listSupportQuestions.mockResolvedValue({ supportQuestions: [original] })
    await act(async () => result.current.refresh())
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
  })

  it('initializes selection again for a new session generation', async () => {
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
    act(() => result.current.selectSupportQuestion(null))
    act(() => useAuthStore.setState(state => ({ generation: state.generation + 1 })))
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
  })

  it('streams tokens then stores the complete answer', async () => {
    mocks.streamAssistantAnswer.mockImplementation(async (_channelId, _questionId, handlers) => {
      handlers.onToken('Hello ')
      handlers.onToken('world')
      const answer = {
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
      }
      mocks.fetchAssistantAnswer.mockResolvedValue(answer)
      handlers.onComplete(answer)
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

  it('discards an interrupted draft and permits only explicit source recovery after a provider attempt', async () => {
    mocks.streamAssistantAnswer.mockImplementation(async (_channel, _question, handlers) => {
      handlers.onToken('Unsaved draft')
      throw new AssistantStreamError('STREAM_INTERRUPTED', 'The answer was interrupted.', 502)
    })
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
    await act(() => result.current.invokeAssistant('question-1', { modelId: 'provider-model', answerMode: 'quoted-evidence' }))
    expect(result.current.streamPhase).toBe('error')
    expect(result.current.streamingText).toBe('')
    expect(result.current.selectedAnswer).toBeNull()
    expect(result.current.requiresSourceRecovery).toBe(true)
    await act(() => result.current.invokeAssistant('question-1', { modelId: 'provider-model', answerMode: 'quoted-evidence' }))
    expect(mocks.streamAssistantAnswer).toHaveBeenCalledTimes(1)
    await act(() => result.current.invokeAssistant('question-1', { modelId: 'source-only', answerMode: 'source-only' }))
    expect(mocks.streamAssistantAnswer).toHaveBeenCalledTimes(2)
    expect(mocks.streamAssistantAnswer.mock.lastCall?.[3]).toEqual({ modelId: 'source-only', answerMode: 'source-only' })
  })

  it('ignores superseded stream callbacks and settlement while the next request is active', async () => {
    const streams: { handlers: StreamAssistantHandlers; resolve: () => void }[] = []
    mocks.streamAssistantAnswer.mockImplementation((_channel, _question, handlers) => new Promise<void>((resolve) => streams.push({ handlers, resolve })))
    const { result } = renderHook(() => useQuestionsChannel({ channelId: 'questions-1', cohortId: 'cohort-1' }))
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
    let first!: Promise<void>
    let second!: Promise<void>
    act(() => { first = result.current.invokeAssistant('question-1', { modelId: 'source-only', answerMode: 'source-only' }) })
    act(() => { second = result.current.invokeAssistant('question-1', { modelId: 'source-only', answerMode: 'source-only' }) })
    await act(async () => {
      streams[1].handlers.onToken('Current draft')
      streams[0].handlers.onToken('Stale draft')
      streams[0].resolve()
      await first
    })
    expect(streams[0].handlers.signal?.aborted).toBe(true)
    expect(result.current.streamingText).toBe('Current draft')
    expect(result.current.invokingQuestionId).toBe('question-1')
    expect(result.current.streamPhase).toBe('streaming')
    await act(async () => { streams[1].resolve(); await second })
  })

  it('aborts a channel stream on navigation and ignores its late draft', async () => {
    let handlers!: StreamAssistantHandlers
    let resolve!: () => void
    mocks.streamAssistantAnswer.mockImplementation((_channel, _question, nextHandlers) => {
      handlers = nextHandlers
      return new Promise<void>((done) => { resolve = done })
    })
    const { result, rerender } = renderHook(({ channelId }) => useQuestionsChannel({ channelId, cohortId: 'cohort-1' }), {
      initialProps: { channelId: 'questions-1' },
    })
    await waitFor(() => expect(result.current.selectedQuestion?.id).toBe('question-1'))
    let request!: Promise<void>
    act(() => { request = result.current.invokeAssistant('question-1') })
    rerender({ channelId: 'questions-2' })
    await act(async () => { handlers.onToken('Prior channel content'); resolve(); await request })
    expect(handlers.signal?.aborted).toBe(true)
    expect(result.current.streamingText).toBe('')
    expect(result.current.streamPhase).toBe('idle')
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
    const createdReply = { ...savedReply, id: 'reply-new' }
    let resolveReplyList: ((value: { replies: never[] }) => void) | undefined
    mocks.listSupportQuestionReplies
      .mockImplementationOnce(() => new Promise((resolve) => { resolveReplyList = resolve }))
      .mockResolvedValue({ replies: [createdReply] })
    mocks.postSupportQuestionReply.mockResolvedValue(createdReply)

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
    mocks.listSupportQuestionReplies.mockResolvedValue({ replies: [] })
    await act(() => result.current.refresh())
    await waitFor(() => expect(result.current.selectedReplies).toEqual([]))
  })
})
