import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { HomePage } from './HomePage'
import { fetchHomeSummary } from '../../home/home-summary-api'

vi.mock('../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { id: string; displayName: string } }) => unknown) =>
    selector({ user: { id: 'user-1', displayName: 'Sam Learner' } }),
}))

vi.mock('../../home/home-summary-api', () => ({
  homeSummaryQueryKey: (userId: string | undefined) => ['home-summary', userId],
  fetchHomeSummary: vi.fn(),
}))

vi.mock('../components/HomeStudyServerInvites', () => ({
  HomeStudyServerInvites: () => null,
}))

function renderHome() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('HomePage', () => {
  beforeEach(() => {
    vi.mocked(fetchHomeSummary).mockReset()
  })

  it('renders greeting and continue learning from home summary', async () => {
    vi.mocked(fetchHomeSummary).mockResolvedValue({
      courses: [
        {
          courseId: 'c1',
          studyServerId: 's1',
          title: 'CS 101 — Intro to CS',
          cohortId: 'co1',
          cohortName: 'Spring cohort',
          instructorDisplayName: 'Dr. Ada',
          progress: null,
          progressUnavailableReason: 'NO_CURRICULUM',
          href: '/app/servers/s1/courses/c1/overview?cohort=co1',
        },
      ],
      attention: [],
      upNext: [],
      partialFailures: [],
    })

    renderHome()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Good')
    expect(screen.getByText('Continue learning')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /CS 101 — Intro to CS/i })).toBeInTheDocument()
    })
    expect(screen.getByText('Up next')).toBeInTheDocument()
    expect(screen.getByText('Nothing coming up yet.')).toBeInTheDocument()
    expect(screen.getByText('Progress unavailable')).toBeInTheDocument()
  })
})
