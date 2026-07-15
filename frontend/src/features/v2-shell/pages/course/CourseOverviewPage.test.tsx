import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { CourseOverviewPage } from './CourseOverviewPage'
import { fetchCourseOverviewSummary } from '../../../course-overview/course-overview-summary-api'

const workspace = vi.hoisted(() => ({
  serverId: 'server-1',
  courseId: 'course-1',
  course: {
    channels: [{ id: 'voice-1', cohortId: 'cohort-1', name: 'study-room', kind: 'VOICE' as const }],
  },
  selectedCohort: { id: 'cohort-1', name: 'Summer 2026' },
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => workspace,
}))

vi.mock('../../../course-overview/course-overview-summary-api', () => ({
  courseOverviewSummaryQueryKey: (courseId: string, cohortId: string | undefined) => [
    'course-overview-summary',
    courseId,
    cohortId ?? null,
  ],
  fetchCourseOverviewSummary: vi.fn(),
}))

describe('CourseOverviewPage', () => {
  beforeEach(() => {
    vi.mocked(fetchCourseOverviewSummary).mockReset()
  })

  it('deep-links Study room from overview summary when present', async () => {
    vi.mocked(fetchCourseOverviewSummary).mockResolvedValue({
      progress: null,
      progressUnavailableReason: 'NO_CURRICULUM',
      thisWeek: [
        {
          id: 'oh-1',
          kind: 'OFFICE_HOURS',
          title: 'Office hours',
          detail: 'Today · 2:00 PM',
          actionLabel: 'Open',
          href: '/app/servers/server-1/courses/course-1/office-hours?cohort=cohort-1&session=oh-1',
        },
      ],
      recentActivity: [],
      upNext: [
        {
          id: 'oh-1',
          kind: 'OFFICE_HOURS',
          title: 'Office hours',
          detail: 'Today · 2:00 PM',
          actionLabel: 'Open',
          href: '/app/servers/server-1/courses/course-1/office-hours?cohort=cohort-1&session=oh-1',
        },
        {
          id: 'study-voice-1',
          kind: 'STUDY_ROOM',
          title: 'Study room',
          detail: 'Study room available',
          actionLabel: 'Join',
          href: '/app/servers/server-1/courses/course-1/chat?cohort=cohort-1&channel=voice-1',
        },
      ],
      partialFailures: [],
    })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CourseOverviewPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Join Study room' })).toHaveAttribute(
        'href',
        '/app/servers/server-1/courses/course-1/chat?cohort=cohort-1&channel=voice-1',
      )
    })
    expect(screen.getByText(/Progress is unavailable/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open Office hours' })).toBeInTheDocument()
  })

  it('falls back to workspace voice channel when summary has no study room', async () => {
    vi.mocked(fetchCourseOverviewSummary).mockResolvedValue({
      progress: null,
      progressUnavailableReason: 'NO_CURRICULUM',
      thisWeek: [],
      recentActivity: [],
      upNext: [],
      partialFailures: [],
    })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CourseOverviewPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Join Study room' })).toHaveAttribute(
        'href',
        '/app/servers/server-1/courses/course-1/chat?cohort=cohort-1&channel=voice-1',
      )
    })
  })
})
