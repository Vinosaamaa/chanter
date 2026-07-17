import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CourseChatPage } from './CourseChatPage'

const mocks = vi.hoisted(() => ({
  workspace: {
    serverId: 'server-1',
    courseId: 'course-1',
    serverName: 'Spring Hub',
    course: {
      id: 'course-1',
      title: 'CS 101',
      capabilities: {
        instructor: false,
        teachingAssistant: false,
        enrolled: true,
        canManageCourse: false,
        canManageQuestions: false,
        canApproveFaq: false,
        canManageTaQueue: false,
        canUploadResources: false,
        canScheduleOfficeHours: false,
        canManagePeople: false,
      },
      cohorts: [],
      channels: [] as Array<{
        id: string
        cohortId: string
        name: string
        kind: 'TEXT' | 'VOICE'
      }>,
    },
    studyServerCapabilities: {
      owner: false,
      canTeach: false,
      canCreateCourse: false,
      canManageCommunity: false,
      canManageEvents: false,
      canManageBilling: false,
    },
    courseCapabilities: {
      instructor: false,
      teachingAssistant: false,
      enrolled: true,
      canManageCourse: false,
      canManageQuestions: false,
      canApproveFaq: false,
      canManageTaQueue: false,
      canUploadResources: false,
      canScheduleOfficeHours: false,
      canManagePeople: false,
    },
    selectedCohort: { id: 'cohort-1', name: 'Summer 2026', capabilities: { enrolled: true, teachingAssistant: false, canManage: false } },
    selectCohort: vi.fn(),
    isOwner: false,
    isLoading: false,
    isError: false,
  },
  conversation: {
    messages: [],
    connectionStatus: 'connected' as const,
    isLoadingHistory: false,
    error: null,
    sendMessage: vi.fn(),
    isSending: false,
  },
  useConversation: vi.fn(),
  voice: {
    presences: [],
    status: 'idle' as const,
    error: null,
    presenceError: null as string | null,
    isLoadingPresences: false,
    isMuted: false,
    isBusy: false,
    joinVoice: vi.fn(),
    leaveVoice: vi.fn(),
    toggleMute: vi.fn(),
    refreshPresences: vi.fn(),
  },
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => mocks.workspace,
}))

vi.mock('../../../shell/hooks/use-channel-conversation', () => ({
  useChannelConversation: (...args: unknown[]) => {
    mocks.useConversation(...args)
    return mocks.conversation
  },
}))

vi.mock('../../../voice/hooks/use-voice-channel', () => ({
  useVoiceChannel: () => mocks.voice,
}))

vi.mock('../../../voice/voice-api', () => ({
  fetchVoicePresences: vi.fn().mockResolvedValue([]),
}))

vi.mock('../../../shell/channel-messages-api', () => ({
  fetchChannelMessageAccess: vi.fn().mockResolvedValue({
    channelId: 'announcements-1',
    channelName: 'announcements',
    canReadMessages: true,
    canPostMessages: true,
  }),
}))

vi.mock('../../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { id: string }; accessToken: string }) => unknown) =>
    selector({ user: { id: 'learner-1' }, accessToken: 'token' }),
}))

describe('CourseChatPage', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.workspace.course.channels = []
    mocks.workspace.courseCapabilities.canManageCourse = false
    mocks.workspace.course.capabilities.canManageCourse = false
    mocks.voice.presences = []
    mocks.voice.presenceError = null
    mocks.voice.isLoadingPresences = false
  })

  it('shows a truthful empty state without demo channels or messages', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <CourseChatPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByText('No text channels yet.')).toBeInTheDocument()
    expect(screen.getByText('No voice channels yet.')).toBeInTheDocument()
    expect(screen.queryByText('Dr. Alex Johnson')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'general' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add attachment' })).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: 'Add emoji' })).toHaveAttribute('aria-disabled', 'true')
  })

  it('selects a real channel from the current Cohort and hides other Cohorts', () => {
    mocks.workspace.course.channels = [
      { id: 'channel-1', cohortId: 'cohort-1', name: 'general', kind: 'TEXT' },
      { id: 'channel-2', cohortId: 'cohort-2', name: 'private-cohort', kind: 'TEXT' },
    ]
    mocks.workspace.courseCapabilities.canManageCourse = true
    mocks.workspace.course.capabilities.canManageCourse = true

    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/?channel=channel-1']}>
          <CourseChatPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByRole('button', { name: 'general' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'private-cohort' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add text channel' })).toBeInTheDocument()
    expect(mocks.useConversation).toHaveBeenCalledWith('course', 'channel-1')
  })

  it('traps channel editor focus, closes with Escape, and restores the trigger', async () => {
    const user = userEvent.setup()
    mocks.workspace.course.channels = [
      { id: 'channel-1', cohortId: 'cohort-1', name: 'general', kind: 'TEXT' },
    ]
    mocks.workspace.courseCapabilities.canManageCourse = true

    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/?channel=channel-1']}>
          <CourseChatPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const renameButton = screen.getByRole('button', { name: 'Rename general' })
    await user.click(renameButton)
    expect(screen.getByRole('dialog', { name: 'Rename #general' })).toBeInTheDocument()
    expect(screen.getByLabelText('Channel name')).toHaveFocus()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(renameButton).toHaveFocus())
  })

  it('shows voice loading and failure states without claiming the room is empty', () => {
    mocks.workspace.course.channels = [
      { id: 'voice-1', cohortId: 'cohort-1', name: 'study-room', kind: 'VOICE' },
    ]
    mocks.voice.isLoadingPresences = true
    mocks.voice.presenceError = 'Presence service unavailable'

    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/?channel=voice-1']}>
          <CourseChatPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByText('Loading voice participants…')).toBeInTheDocument()
    expect(screen.queryByText('The room is quiet')).not.toBeInTheDocument()
  })
})
