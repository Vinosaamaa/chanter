import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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
  afterEach(cleanup)
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

  it('retries a failed Home summary and gives a new learner a real join route', async () => {
    const user = userEvent.setup()
    vi.mocked(fetchHomeSummary).mockRejectedValueOnce(new Error('Offline')).mockResolvedValueOnce({
      courses: [], attention: [], upNext: [], partialFailures: [],
    })
    renderHome()
    await user.click(await screen.findByRole('button', { name: 'Try again' }))
    const join = await screen.findByRole('link', { name: 'Join or create a Study Server' })
    expect(join).toHaveAttribute('href', '/app/onboarding/join-or-create')
    expect(fetchHomeSummary).toHaveBeenCalledTimes(2)
  })
})
