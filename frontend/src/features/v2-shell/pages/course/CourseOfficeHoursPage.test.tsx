import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { OfficeHoursSession } from '../../../support-operations/support-operations-types'
import { CourseOfficeHoursPage } from './CourseOfficeHoursPage'

const mocks = vi.hoisted(() => ({
  officeHours: {
    sessions: [
      {
        id: 'live-session',
        cohortId: 'cohort-1',
        voiceChannelId: 'voice-1',
        scheduledByUserId: 'owner-1',
        startsAt: '2026-07-14T20:00:00.000Z',
        endsAt: '2026-07-14T21:00:00.000Z',
        status: 'LIVE',
        createdAt: '2026-07-13T20:00:00.000Z',
      },
    ],
    activeSession: {
      id: 'live-session',
      cohortId: 'cohort-1',
      voiceChannelId: 'voice-1',
      scheduledByUserId: 'owner-1',
      startsAt: '2026-07-14T20:00:00.000Z',
      endsAt: '2026-07-14T21:00:00.000Z',
      status: 'LIVE',
      createdAt: '2026-07-13T20:00:00.000Z',
    },
    liveSession: {
      id: 'live-session',
      cohortId: 'cohort-1',
      voiceChannelId: 'voice-1',
      scheduledByUserId: 'owner-1',
      startsAt: '2026-07-14T20:00:00.000Z',
      endsAt: '2026-07-14T21:00:00.000Z',
      status: 'LIVE',
      createdAt: '2026-07-13T20:00:00.000Z',
    } as OfficeHoursSession | null,
    upcomingSessions: [] as OfficeHoursSession[],
    participants: [
      {
        sessionId: 'live-session',
        userId: 'owner-1',
        canSpeak: true,
        handRaised: false,
        joinedAt: '2026-07-14T20:00:00.000Z',
        updatedAt: '2026-07-14T20:00:00.000Z',
      },
      {
        sessionId: 'live-session',
        userId: 'learner-1',
        canSpeak: false,
        handRaised: true,
        joinedAt: '2026-07-14T20:02:00.000Z',
        updatedAt: '2026-07-14T20:03:00.000Z',
      },
    ],
    profilesById: {
      'owner-1': { userId: 'owner-1', displayName: 'Instructor Ada' },
      'learner-1': { userId: 'learner-1', displayName: 'Learner Lin' },
    },
    currentParticipant: {
      sessionId: 'live-session',
      userId: 'owner-1',
      canSpeak: true,
      handRaised: false,
      joinedAt: '2026-07-14T20:00:00.000Z',
      updatedAt: '2026-07-14T20:00:00.000Z',
    },
    isLoading: false,
    accessDenied: false,
    canSchedule: true,
    canJoin: false,
    canManage: true,
    error: null,
    actionMessage: null,
    isBusy: false,
    refresh: vi.fn(),
    scheduleSession: vi.fn(),
    updateSession: vi.fn(),
    cancelSession: vi.fn(),
    startSession: vi.fn(),
    joinSession: vi.fn(),
    setHandRaised: vi.fn(),
    grantSpeaking: vi.fn(),
    leaveSession: vi.fn(),
    endSession: vi.fn(),
  },
  voice: {
    status: 'connected',
    error: null,
    isMuted: false,
    isBusy: false,
    canSpeak: true,
    joinVoice: vi.fn(),
    leaveVoice: vi.fn(),
    refreshPermissions: vi.fn(),
    toggleMute: vi.fn(),
  },
  workspace: {
    course: {
      id: 'course-1',
      title: 'Distributed Systems',
      channels: [{ id: 'voice-1', name: 'study-room', kind: 'VOICE' }],
    },
    courseCapabilities: { canScheduleOfficeHours: true },
    selectedCohort: { id: 'cohort-1', name: 'Summer 2026' },
  },
}))

vi.mock('../../../support-operations/hooks/use-office-hours-panel', () => ({
  useOfficeHoursPanel: () => mocks.officeHours,
}))

vi.mock('../../../voice/hooks/use-office-hours-voice', () => ({
  useOfficeHoursVoice: () => mocks.voice,
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => mocks.workspace,
}))

describe('CourseOfficeHoursPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    window.history.replaceState({}, '', '/')
    mocks.workspace.courseCapabilities.canScheduleOfficeHours = true
    mocks.officeHours.canSchedule = true
    mocks.officeHours.canJoin = false
    mocks.officeHours.canManage = true
    mocks.officeHours.liveSession = {
      ...mocks.officeHours.sessions[0],
      status: 'LIVE',
    }
    mocks.officeHours.upcomingSessions = []
    mocks.officeHours.currentParticipant = mocks.officeHours.participants[0]
    mocks.voice.status = 'connected'
    mocks.voice.canSpeak = true
    mocks.voice.isMuted = false
    mocks.officeHours.scheduleSession.mockResolvedValue(true)
    mocks.officeHours.joinSession.mockResolvedValue(mocks.officeHours.participants[1])
  })

  it('renders durable session timing and participant profiles without fixtures', () => {
    render(<CourseOfficeHoursPage />)

    expect(screen.getByText('Instructor Ada')).toBeInTheDocument()
    expect(screen.getByText('Learner Lin')).toBeInTheDocument()
    expect(screen.getByText(/Jul 14/)).toBeInTheDocument()
    expect(screen.queryByText('Dr. Alex Johnson')).not.toBeInTheDocument()
    expect(screen.queryByText('Daniel Anderson')).not.toBeInTheDocument()
  })

  it('submits a durable one-time schedule with the selected duration', async () => {
    const user = userEvent.setup()
    render(<CourseOfficeHoursPage />)

    await user.click(screen.getByRole('button', { name: /schedule/i }))
    const startInput = screen.getByLabelText('Start date and time')
    fireEvent.change(startInput, { target: { value: '2026-07-20T10:30' } })
    const durationInput = screen.getByRole('spinbutton', { name: 'Duration in minutes' })
    await user.clear(durationInput)
    await user.type(durationInput, '90')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    const startsAt = new Date('2026-07-20T10:30').toISOString()
    const endsAt = new Date(new Date('2026-07-20T10:30').getTime() + 90 * 60_000).toISOString()
    expect(mocks.officeHours.scheduleSession).toHaveBeenCalledWith({ startsAt, endsAt })
  })

  it('rejects an empty duration without dispatching an invalid timestamp', async () => {
    const user = userEvent.setup()
    render(<CourseOfficeHoursPage />)

    await user.click(screen.getByRole('button', { name: /schedule/i }))
    await user.clear(screen.getByRole('spinbutton', { name: 'Duration in minutes' }))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(screen.getByText(/duration of at least 15 minutes/i)).toBeInTheDocument()
    expect(mocks.officeHours.scheduleSession).not.toHaveBeenCalled()
  })

  it('lets the instructor grant speaking access from the raised-hand roster', async () => {
    const user = userEvent.setup()
    render(<CourseOfficeHoursPage />)

    await user.click(screen.getByRole('button', { name: 'Grant speaking access to Learner Lin' }))

    expect(mocks.officeHours.grantSpeaking).toHaveBeenCalledWith('learner-1', true)
  })

  it('lets a joined learner raise a hand and leave while mute remains permission-gated', async () => {
    const user = userEvent.setup()
    mocks.workspace.courseCapabilities.canScheduleOfficeHours = false
    mocks.officeHours.canSchedule = false
    mocks.officeHours.canJoin = true
    mocks.officeHours.canManage = false
    mocks.officeHours.currentParticipant = mocks.officeHours.participants[1]
    mocks.voice.canSpeak = false
    render(<CourseOfficeHoursPage />)

    const muteButton = screen.getByRole('button', { name: 'Mute microphone' })
    expect(muteButton).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Lower Hand' }))
    expect(mocks.officeHours.setHandRaised).toHaveBeenCalledWith(false)

    await user.click(screen.getByRole('button', { name: 'Leave' }))
    expect(mocks.voice.leaveVoice).toHaveBeenCalledOnce()
    expect(mocks.officeHours.leaveSession).toHaveBeenCalledOnce()
  })

  it('highlights the exact scheduled session selected by the Teaching deep link', () => {
    mocks.officeHours.liveSession = null
    mocks.officeHours.upcomingSessions = [
      {
        ...mocks.officeHours.sessions[0],
        id: 'session-first',
        status: 'SCHEDULED',
        startsAt: '2026-07-15T20:00:00.000Z',
        endsAt: '2026-07-15T21:00:00.000Z',
      },
      {
        ...mocks.officeHours.sessions[0],
        id: 'session-target',
        status: 'SCHEDULED',
        startsAt: '2026-07-16T20:00:00.000Z',
        endsAt: '2026-07-16T21:00:00.000Z',
      },
    ]
    window.history.replaceState({}, '', '/office-hours?session=session-target')

    render(<CourseOfficeHoursPage />)

    expect(
      screen.getAllByText(/Thu, Jul 16/)
        .map((element) => element.closest('article'))
        .find((article) => article?.classList.contains('highlighted')),
    ).toHaveClass('highlighted')
  })
})
