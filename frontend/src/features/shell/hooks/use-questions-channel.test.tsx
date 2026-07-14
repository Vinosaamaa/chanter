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
        status: 'RESOLVED',
        createdAt: '2026-07-14T20:00:00.000Z',
      }],
    })
    mocks.listSupportQuestionReplies.mockResolvedValue({ replies: [] })
    mocks.fetchPublicProfiles.mockImplementation((userIds: string[]) => Promise.resolve({
      profiles: userIds.map((userId) => ({ userId, displayName: userId })),
    }))
    mocks.fetchAssistantAnswer.mockResolvedValue({
      id: 'answer-1',
      supportQuestionId: 'question-1',
      answerBody: 'The deadline is Friday.',
      supportQuestionStatus: 'AI_ANSWERED',
      handoffRecommended: false,
      sources: [],
      createdAt: '2026-07-14T20:01:00.000Z',
    })
  })

  it('reloads a persisted assistant answer after the question is resolved', async () => {
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
