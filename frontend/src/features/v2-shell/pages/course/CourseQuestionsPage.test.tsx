import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  AssistantAnswer,
  SupportQuestionReply,
  SupportQuestionSummary,
  TaQueueItem,
} from '../../../questions/support-question-types'
import { CourseQuestionsPage } from './CourseQuestionsPage'

const mocks = vi.hoisted(() => ({
  questions: {
    supportQuestions: [
      {
        id: 'question-1',
        channelMessageId: 'message-1',
        channelId: 'questions-1',
        senderUserId: 'learner-1',
        body: 'Why does the recursive call stop?',
        status: 'HUMAN_ANSWERED',
        createdAt: '2026-07-14T20:00:00.000Z',
      },
    ] as SupportQuestionSummary[],
    selectedSupportQuestionId: 'question-1',
    selectedQuestion: {
      id: 'question-1',
      channelMessageId: 'message-1',
      channelId: 'questions-1',
      senderUserId: 'learner-1',
      body: 'Why does the recursive call stop?',
      status: 'HUMAN_ANSWERED',
      createdAt: '2026-07-14T20:00:00.000Z',
    } as SupportQuestionSummary | null,
    selectedAnswer: null as AssistantAnswer | null,
    selectedReplies: [
      {
        id: 'reply-1',
        supportQuestionId: 'question-1',
        authorUserId: 'instructor-1',
        body: 'The base case returns without another recursive call.',
        createdAt: '2026-07-14T20:05:00.000Z',
      },
    ] as SupportQuestionReply[],
    profilesById: {
      'learner-1': { userId: 'learner-1', displayName: 'Learner Lin' },
      'instructor-1': { userId: 'instructor-1', displayName: 'Instructor Ada' },
    },
    timeline: [],
    isLoadingHistory: false,
    error: null,
    postQuestion: vi.fn(),
    isPosting: false,
    invokeAssistant: vi.fn(),
    invokingQuestionId: null,
    addToTaQueue: vi.fn(),
    addingToQueueQuestionId: null,
    taQueueSuccess: null,
    selectSupportQuestion: vi.fn(),
    postReply: vi.fn(),
    isPostingReply: false,
    moderateQuestion: vi.fn(),
    isModerating: false,
    refresh: vi.fn(),
  },
  workspace: {
    serverId: 'server-1',
    course: {
      id: 'course-1',
      title: 'Distributed Systems',
      channels: [{ id: 'questions-1', name: 'questions', kind: 'TEXT' }],
    },
    courseCapabilities: {
      canManageQuestions: true,
      canApproveFaq: true,
      canManageTaQueue: true,
    },
    selectedCohort: { id: 'cohort-1', name: 'Summer 2026' },
  },
  faq: {
    candidates: [],
    approvedFaqs: [],
    selectedIndex: 0,
    questionDraft: '',
    setQuestionDraft: vi.fn(),
    answerDraft: '',
    setAnswerDraft: vi.fn(),
    editingFaqId: null,
    isLoading: false,
    accessDenied: false,
    error: null,
    actionMessage: null,
    isSaving: false,
    refresh: vi.fn(),
    selectCandidate: vi.fn(),
    startEditApproved: vi.fn(),
    clearEdit: vi.fn(),
    approveOrUpdate: vi.fn(),
  },
  queue: {
    items: [] as TaQueueItem[],
    profilesById: {},
    isLoading: false,
    accessDenied: false,
    canManage: true,
    error: null,
    actionMessage: null,
    actingItemId: null,
    refresh: vi.fn(),
    pickupItem: vi.fn(),
    resolveItem: vi.fn(),
    cancelItem: vi.fn(),
  },
}))

vi.mock('../../../shell/hooks/use-questions-channel', () => ({
  useQuestionsChannel: () => mocks.questions,
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => mocks.workspace,
}))

vi.mock('../../../support-operations/hooks/use-faq-approval-panel', () => ({
  useFaqApprovalPanel: () => mocks.faq,
}))

vi.mock('../../../support-operations/hooks/use-ta-queue-panel', () => ({
  useTaQueuePanel: () => mocks.queue,
}))

describe('CourseQuestionsPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    const question: SupportQuestionSummary = {
      id: 'question-1',
      channelMessageId: 'message-1',
      channelId: 'questions-1',
      senderUserId: 'learner-1',
      body: 'Why does the recursive call stop?',
      status: 'HUMAN_ANSWERED',
      createdAt: '2026-07-14T20:00:00.000Z',
    }
    mocks.questions.supportQuestions = [question]
    mocks.questions.selectedSupportQuestionId = question.id
    mocks.questions.selectedQuestion = question
    mocks.questions.selectedAnswer = null
    mocks.questions.selectedReplies = [{
      id: 'reply-1',
      supportQuestionId: question.id,
      authorUserId: 'instructor-1',
      body: 'The base case returns without another recursive call.',
      createdAt: '2026-07-14T20:05:00.000Z',
    }]
    mocks.queue.items = []
  })

  it('renders only durable questions, profiles, and persisted replies', () => {
    render(<CourseQuestionsPage />)

    expect(screen.getAllByText('Why does the recursive call stop?')).toHaveLength(2)
    expect(screen.getByText('Learner Lin')).toBeInTheDocument()
    expect(screen.getByText('Instructor Ada')).toBeInTheDocument()
    expect(screen.getByText('The base case returns without another recursive call.')).toBeInTheDocument()
    expect(screen.queryByText('How do I trace recursion on merge sort?')).not.toBeInTheDocument()
    expect(screen.queryByText('Big-O for nested loops?')).not.toBeInTheDocument()
  })

  it('posts a teaching reply instead of creating a learner question', async () => {
    const user = userEvent.setup()
    mocks.questions.postReply.mockResolvedValue(true)
    render(<CourseQuestionsPage />)

    await user.type(screen.getByPlaceholderText('Reply to this question…'), 'Try the base case first.')
    await user.click(screen.getByRole('button', { name: 'Send reply' }))

    expect(mocks.questions.postReply).toHaveBeenCalledWith(
      'question-1',
      'Try the base case first.',
    )
    expect(mocks.questions.postQuestion).not.toHaveBeenCalled()
  })

  it('clears a thread-specific draft when another question is selected', async () => {
    const user = userEvent.setup()
    mocks.questions.supportQuestions = [
      mocks.questions.supportQuestions[0],
      {
        ...mocks.questions.supportQuestions[0],
        id: 'question-2',
        body: 'How does memoization help?',
      },
    ]
    render(<CourseQuestionsPage />)

    const composer = screen.getByPlaceholderText('Reply to this question…')
    await user.type(composer, 'This belongs to question one.')
    await user.click(screen.getByRole('button', { name: /how does memoization help/i }))

    expect(mocks.questions.selectSupportQuestion).toHaveBeenCalledWith('question-2')
    expect(composer).toHaveValue('')
  })

  it('does not erase a new draft when an older reply submission completes', async () => {
    const user = userEvent.setup()
    let resolveReply: ((saved: boolean) => void) | undefined
    mocks.questions.postReply.mockReturnValue(new Promise((resolve) => {
      resolveReply = resolve
    }))
    render(<CourseQuestionsPage />)

    const composer = screen.getByPlaceholderText('Reply to this question…')
    await user.type(composer, 'First reply')
    await user.click(screen.getByRole('button', { name: 'Send reply' }))
    await user.clear(composer)
    await user.type(composer, 'A new draft')

    await act(async () => resolveReply?.(true))

    expect(composer).toHaveValue('A new draft')
  })

  it('does not offer learner handoff controls to teaching staff', () => {
    mocks.questions.selectedAnswer = {
      id: 'answer-1',
      supportQuestionId: 'question-1',
      channelId: 'questions-1',
      studyServerId: 'server-1',
      learnerUserId: 'learner-1',
      questionBody: 'Why does the recursive call stop?',
      answerBody: 'I am not confident enough to answer.',
      confidence: 'LOW',
      supportQuestionStatus: 'AI_LOW_CONFIDENCE',
      handoffRecommended: true,
      sources: [],
      createdAt: '2026-07-14T20:02:00.000Z',
    }

    render(<CourseQuestionsPage />)

    expect(screen.queryByRole('button', { name: /add to ta queue/i })).not.toBeInTheDocument()
  })

  it('keeps the active filter aligned with the selected thread status', async () => {
    mocks.questions.supportQuestions = [
      {
        ...mocks.questions.supportQuestions[0],
        id: 'open-question',
        status: 'UNANSWERED',
      },
      {
        ...mocks.questions.supportQuestions[0],
        id: 'resolved-question',
        status: 'RESOLVED',
      },
    ]
    mocks.questions.selectedSupportQuestionId = 'resolved-question'
    mocks.questions.selectedQuestion = {
      ...mocks.questions.supportQuestions[1],
      id: 'resolved-question',
      status: 'RESOLVED',
    }

    render(<CourseQuestionsPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Answered' })).toHaveClass('active')
    })
  })

  it('refreshes the selected question after resolving its TA queue item', async () => {
    const user = userEvent.setup()
    mocks.queue.items = [{
      id: 'queue-1',
      cohortId: 'cohort-1',
      supportQuestionId: 'resolved-question',
      channelId: 'questions-1',
      learnerUserId: 'learner-1',
      body: 'Why does the recursive call stop?',
      status: 'PICKED_UP',
      assignedTaUserId: 'instructor-1',
      createdAt: '2026-07-14T20:02:00.000Z',
      updatedAt: '2026-07-14T20:03:00.000Z',
    }]
    mocks.queue.resolveItem.mockResolvedValue(undefined)

    render(<CourseQuestionsPage />)
    await user.click(screen.getByRole('button', { name: /resolve ta queue question/i }))

    await waitFor(() => {
      expect(mocks.queue.resolveItem).toHaveBeenCalledWith('queue-1')
      expect(mocks.questions.refresh).toHaveBeenCalled()
    })
  })

  it('selects the queue question before a TA picks it up', async () => {
    const user = userEvent.setup()
    mocks.queue.items = [{
      id: 'queue-1',
      cohortId: 'cohort-1',
      supportQuestionId: 'question-2',
      channelId: 'questions-1',
      learnerUserId: 'learner-1',
      body: 'How does memoization help?',
      status: 'OPEN',
      assignedTaUserId: null,
      createdAt: '2026-07-14T20:02:00.000Z',
      updatedAt: '2026-07-14T20:03:00.000Z',
    }]
    mocks.queue.pickupItem.mockResolvedValue(undefined)

    render(<CourseQuestionsPage />)
    await user.click(screen.getByRole('button', { name: /pick up ta queue question/i }))

    expect(mocks.questions.selectSupportQuestion).toHaveBeenCalledWith('question-2')
    expect(mocks.queue.pickupItem).toHaveBeenCalledWith('queue-1')
  })
})
