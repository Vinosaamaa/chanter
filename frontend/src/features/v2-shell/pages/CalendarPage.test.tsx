import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { CalendarPage } from './CalendarPage'

const fetchCalendar = vi.fn()
const upsertCommunityEventRsvp = vi.fn()

vi.mock('../../calendar/calendar-api', () => ({
  calendarQueryKey: (
    userId: string | undefined,
    query: { from: string; to: string; types?: string; search?: string },
  ) => ['calendar', userId, query.from, query.to, query.types ?? '', query.search ?? ''],
  fetchCalendar: (...args: unknown[]) => fetchCalendar(...args),
}))

vi.mock('../../community-events/community-events-api', () => ({
  upsertCommunityEventRsvp: (...args: unknown[]) => upsertCommunityEventRsvp(...args),
}))

const ohStarts = new Date()
ohStarts.setHours(14, 0, 0, 0)
const eventStarts = new Date()
eventStarts.setHours(18, 0, 0, 0)

const sampleItems = [
  {
    id: 'oh-1',
    type: 'OFFICE_HOURS',
    title: 'Office hours',
    contextLabel: 'CS 101',
    startsAt: ohStarts.toISOString(),
    endsAt: new Date(ohStarts.getTime() + 3_600_000).toISOString(),
    href: '/app/servers/s1/courses/c1/office-hours?cohort=co1&session=oh1',
    actionLabel: 'Join',
    actionKind: 'JOIN',
    viewerRsvp: null,
    studyServerId: 's1',
    courseId: 'c1',
    cohortId: 'co1',
    sourceId: 'oh1',
  },
  {
    id: 'event-1',
    type: 'EVENT',
    title: 'Hackathon kickoff',
    contextLabel: 'Spring Bootcamp Hub',
    startsAt: eventStarts.toISOString(),
    endsAt: new Date(eventStarts.getTime() + 3_600_000).toISOString(),
    href: '/app/servers/s1/community/events?event=e1',
    actionLabel: 'Going',
    actionKind: 'RSVP',
    viewerRsvp: null,
    studyServerId: 's1',
    courseId: null,
    cohortId: null,
    sourceId: 'e1',
  },
]

function renderCalendar(initialEntry = '/app/calendar') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <CalendarPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CalendarPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 'user-1', email: 'sam@example.com', displayName: 'Sam Lee' },
    })
    fetchCalendar.mockResolvedValue({ items: sampleItems, notes: ['Deadlines are omitted'] })
    upsertCommunityEventRsvp.mockResolvedValue({ id: 'e1', viewerRsvp: 'GOING' })
  })

  it('loads calendar items and wires Join / Going actions', async () => {
    const user = userEvent.setup()
    renderCalendar()

    expect(await screen.findAllByText('Hackathon kickoff')).not.toHaveLength(0)
    expect(screen.getByRole('link', { name: 'Join' })).toHaveAttribute(
      'href',
      '/app/servers/s1/courses/c1/office-hours?cohort=co1&session=oh1',
    )

    const rsvpButtons = screen.getAllByRole('button', { name: /^Going$/i })
    const agendaGoing = rsvpButtons.find((button) => button.closest('.agenda-card'))
    expect(agendaGoing).toBeTruthy()
    await user.click(agendaGoing!)
    await waitFor(() => {
      expect(upsertCommunityEventRsvp).toHaveBeenCalledWith('s1', 'e1', 'GOING')
    })
  })

  it('passes type filter to the API', async () => {
    const user = userEvent.setup()
    renderCalendar()
    await screen.findAllByText('Hackathon kickoff')

    fetchCalendar.mockClear()
    fetchCalendar.mockResolvedValue({ items: [], notes: [] })
    await user.click(screen.getByRole('button', { name: /^Office hours$/i }))

    await waitFor(() => {
      expect(fetchCalendar).toHaveBeenCalledWith(
        expect.objectContaining({ types: 'OFFICE_HOURS' }),
      )
    })
  })

  it('passes route search query to the API', async () => {
    renderCalendar('/app/calendar?q=hackathon')
    await waitFor(() => {
      expect(fetchCalendar).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'hackathon' }),
      )
    })
    expect(await screen.findByText(/Filtering by/i)).toBeInTheDocument()
  })

  it('shows empty and error states', async () => {
    fetchCalendar.mockResolvedValueOnce({ items: [], notes: [] })
    const { unmount } = renderCalendar()
    expect(await screen.findByText(/Nothing scheduled this day/i)).toBeInTheDocument()
    unmount()

    fetchCalendar.mockRejectedValueOnce(new Error(''))
    renderCalendar()
    expect(await screen.findByText(/Unable to load calendar/i)).toBeInTheDocument()
  })
})
