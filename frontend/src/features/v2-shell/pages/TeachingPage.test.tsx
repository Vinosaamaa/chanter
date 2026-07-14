import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'

import { TeachingPage } from './TeachingPage'

const mocks = vi.hoisted(() => ({
  access: {
    isLoading: false,
    showTeachingNav: true,
  },
  dashboardPage: {
    servers: [{ id: 'server-1', name: 'Systems Guild' }],
    selectedServerId: 'server-1',
    setSelectedServerId: vi.fn(),
    dashboard: {
      studyServerId: 'server-1',
      planTier: 'PRO',
      unansweredSupportQuestions: 2,
      repeatedQuestionGroups: 1,
      approvedFaqCount: 3,
      openTaQueueItems: 1,
      liveOfficeHoursSessions: 1,
      scheduledOfficeHoursSessions: 0,
      officeHoursWaitlistEntries: 0,
      aiInvocationCount: 12,
      aiInvocationLimit: 100,
      remainingAiInvocations: 88,
      quotaExhausted: false,
      lowConfidenceHandoffs: 1,
      courses: [
        {
          courseId: 'course-1',
          title: 'Distributed Systems',
          questionChannelId: 'questions-1',
          cohorts: [{ cohortId: 'cohort-1', name: 'Summer 2026', openTaQueueItems: 1 }],
          unansweredSupportQuestions: 2,
          repeatedQuestionGroups: 1,
          approvedFaqCount: 3,
          openTaQueueItems: 1,
        },
      ],
    },
    isLoading: false,
    error: null,
  },
  listOfficeHoursSessions: vi.fn(),
}))

vi.mock('../hooks/use-v2-sidebar-data', () => ({
  useV2SidebarData: () => mocks.access,
}))

vi.mock('../../instructor-dashboard/hooks/use-instructor-dashboard-page', () => ({
  useInstructorDashboardPage: () => mocks.dashboardPage,
}))

vi.mock('../../support-operations/office-hours-api', () => ({
  listOfficeHoursSessions: mocks.listOfficeHoursSessions,
}))

function LocationProbe() {
  return <output data-testid="location">{useLocation().pathname}{useLocation().search}</output>
}

describe('TeachingPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.dashboardPage.dashboard.courses[0].cohorts = [
      { cohortId: 'cohort-1', name: 'Summer 2026', openTaQueueItems: 1 },
    ]
    mocks.listOfficeHoursSessions.mockResolvedValue({
      officeHoursSessions: [{
        id: 'session-1',
        cohortId: 'cohort-1',
        voiceChannelId: 'voice-1',
        scheduledByUserId: 'instructor-1',
        startsAt: '2026-07-14T20:00:00.000Z',
        endsAt: '2026-07-14T21:00:00.000Z',
        status: 'LIVE',
        createdAt: '2026-07-13T20:00:00.000Z',
      }],
    })
  })

  it('renders real course metrics and deep-links to the exact question and Office Hours contexts', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/app/teaching']}>
        <Routes>
          <Route path="*" element={<><TeachingPage /><LocationProbe /></>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getAllByText('Distributed Systems').length).toBeGreaterThan(0)
    expect(screen.queryByText('CS 101')).not.toBeInTheDocument()
    expect(screen.getAllByText('2').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: /open questions for distributed systems/i }))
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/app/servers/server-1/courses/course-1/questions?cohort=cohort-1',
    )

    await waitFor(() => expect(screen.getByRole('button', { name: /join distributed systems office hours/i })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /join distributed systems office hours/i }))
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/app/servers/server-1/courses/course-1/office-hours?cohort=cohort-1&session=session-1',
    )
  })

  it('opens the cohort that actually has TA queue work', async () => {
    const user = userEvent.setup()
    mocks.dashboardPage.dashboard.courses[0].cohorts = [
      { cohortId: 'cohort-1', name: 'Summer 2026', openTaQueueItems: 0 },
      { cohortId: 'cohort-2', name: 'Fall 2026', openTaQueueItems: 1 },
    ]

    render(
      <MemoryRouter initialEntries={['/app/teaching']}>
        <Routes>
          <Route path="*" element={<><TeachingPage /><LocationProbe /></>} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: 'View queues' }))

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/app/servers/server-1/courses/course-1/questions?cohort=cohort-2',
    )
  })
})
