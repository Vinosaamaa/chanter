import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  AssistantAnswer,
  SupportQuestionReply,
  SupportQuestionSummary,
  TaQueueItem,
} from '../../../questions/support-question-types'
import { useAuthStore } from '../../../../stores/auth-store'
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
    streamingText: '',
    streamPhase: 'idle' as const,
    markHelpful: vi.fn(),
    markingHelpfulQuestionId: null,
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
  afterEach(() => {
    cleanup()
    useAuthStore.setState(useAuthStore.getInitialState())
  })

  it('opens a phone reading pane and returns to its question list', async () => {
    const user = userEvent.setup()
    render(<CourseQuestionsPage />)
    const question = screen.getByRole('button', { name: /Why does the recursive call stop/ })
      await user.click(question)
      expect(question.closest('.questions-layout')).toHaveClass('question-reading-open')
      expect(screen.getByRole('region', { name: 'Question conversation' })).toHaveFocus()
      await user.click(screen.getByRole('button', { name: 'Back to questions' }))
      expect(question.closest('.questions-layout')).not.toHaveClass('question-reading-open')
      expect(question).toHaveFocus()
    })

    it('opens the learner composer and returns focus to Ask a question', async () => {
      mocks.workspace.courseCapabilities.canManageQuestions = false
      mocks.questions.selectedQuestion = null
      const user = userEvent.setup()
      render(<CourseQuestionsPage />)
      const ask = screen.getByRole('button', { name: 'Ask a question' })
      await user.click(ask)
      expect(screen.getByRole('textbox', { name: 'Ask a support question' })).toHaveFocus()
      await user.click(screen.getByRole('button', { name: 'Back to questions' }))
      expect(ask).toHaveFocus()
    })

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.workspace.courseCapabilities.canManageQuestions = true
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

  it('never submits a retained staff draft to an asynchronously selected question', async () => {
    mocks.questions.postReply.mockResolvedValue(true)
    const original = mocks.questions.selectedQuestion!
    const user = userEvent.setup()
    const view = render(<CourseQuestionsPage />)
    await user.click(screen.getByRole('button', { name: /Why does the recursive call stop/ }))
    const composer = screen.getByRole('textbox', { name: 'Reply to this question' })
    await user.type(composer, 'Reply intended only for question one')
    mocks.questions.selectedQuestion = { ...original, id: 'question-2', body: 'A different question' }
    mocks.questions.selectedSupportQuestionId = 'question-2'
    view.rerender(<CourseQuestionsPage />)
    expect(composer).toHaveValue('Reply intended only for question one')
    fireEvent.submit(composer.closest('form')!)
    expect(mocks.questions.postReply).not.toHaveBeenCalled()
    expect(composer).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Send reply' })).toBeDisabled()
    mocks.questions.selectedQuestion = original
    mocks.questions.selectedSupportQuestionId = original.id
    view.rerender(<CourseQuestionsPage />)
    await user.click(screen.getByRole('button', { name: 'Send reply' }))
    expect(mocks.questions.postReply).toHaveBeenCalledWith(original.id, 'Reply intended only for question one')
  })

  it('keeps a staff draft visible but cannot submit while its question is unavailable', async () => {
    const view = render(<CourseQuestionsPage />)
    const composer = screen.getByRole('textbox', { name: 'Reply to this question' })
    await userEvent.setup().type(composer, 'Keep this unsent reply')
    mocks.questions.selectedQuestion = null
    view.rerender(<CourseQuestionsPage />)
    expect(screen.getByRole('textbox', { name: 'Reply to this question' })).toHaveValue('Keep this unsent reply')
    fireEvent.submit(composer.closest('form')!)
    expect(mocks.questions.postReply).not.toHaveBeenCalled()
  })

  it('does not carry a private reply draft into another session', async () => {
    render(<CourseQuestionsPage />)
    await userEvent.setup().type(screen.getByRole('textbox', { name: 'Reply to this question' }), 'Private unsent reply')
    act(() => useAuthStore.setState(state => ({ generation: state.generation + 1 })))
    expect(screen.getByRole('textbox', { name: 'Reply to this question' })).toHaveValue('')
    expect(mocks.questions.postReply).not.toHaveBeenCalled()
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

  it('shows learners the saved answer’s actual provider and model', () => {
    useAuthStore.setState({ user: { id: 'learner-1', displayName: 'Learner Lin', email: 'learner@example.test' } })
    mocks.workspace.courseCapabilities.canManageQuestions = false
    mocks.questions.selectedAnswer = {
      id: 'saved-answer', supportQuestionId: 'question-1', channelId: 'questions-1', studyServerId: 'server-1', learnerUserId: 'learner-1',
      questionBody: 'Question', answerBody: 'Persisted quotation', confidence: 'HIGH', supportQuestionStatus: 'AI_ANSWERED', handoffRecommended: false,
      sources: [], createdAt: '2026-07-14T20:02:00.000Z',
      audit: { llmUsed: true, llmProvider: 'actual-provider', llmModel: 'saved-model', sourceCount: 1, invocationType: 'GROUNDED_ANSWER', createdAt: '2026-07-14T20:02:00.000Z' },
    }
    render(<CourseQuestionsPage />)
    expect(screen.getByText(/actual-provider \/ saved-model/)).toBeInTheDocument()
    mocks.workspace.courseCapabilities.canManageQuestions = true
  })

  it.each(['CLIENT_REPORTED', undefined] as const)('labels native execution as a client report with unavailable token usage (%s)', (executionProvenance) => {
    mocks.questions.selectedAnswer = {
      id: 'native-answer', supportQuestionId: 'question-1', channelId: 'questions-1', studyServerId: 'server-1', learnerUserId: 'learner-1',
      questionBody: 'Question', answerBody: 'Persisted quotation', confidence: 'HIGH', supportQuestionStatus: 'AI_ANSWERED', handoffRecommended: false,
      sources: [], createdAt: '2026-07-14T20:02:00.000Z',
      audit: { llmUsed: true, llmProvider: 'codex-native', llmModel: 'fixture-native', executionProvenance, sourceCount: 1, invocationType: 'GROUNDED_ANSWER', createdAt: '2026-07-14T20:02:00.000Z' },
    }
    render(<CourseQuestionsPage />)
    expect(screen.getByText(/Reported by your desktop app\. Token usage unavailable\./)).toBeInTheDocument()
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
