import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPublicProfiles } from '../../../friends/friends-api'
import {
  fetchCourseCatalog,
  joinDiscoveredCohort,
} from '../../../course-discovery/course-discovery-api'

import { CommunityDiscoverPage } from './CommunityPages'

vi.mock('../../../course-discovery/course-discovery-api', async () => {
  const actual = await vi.importActual('../../../course-discovery/course-discovery-api')
  return {
    ...actual,
    fetchCourseCatalog: vi.fn(),
    joinDiscoveredCohort: vi.fn(),
  }
})

vi.mock('../../../friends/friends-api', () => ({
  fetchPublicProfiles: vi.fn(),
}))

vi.mock('../../layouts/v2-community-context', () => ({
  useV2Community: () => ({
    serverId: 'server-1',
    serverName: 'Spring Bootcamp Hub',
    studyServerCapabilities: { canCreateCourse: false },
  }),
}))

const catalog = {
  courses: [
    {
      id: 'course-open',
      title: 'MATH 201 - Linear Algebra',
      instructorUserId: 'instructor-1',
      cohorts: [{
        id: 'cohort-open',
        name: 'Fall cohort',
        enrollmentPolicy: 'OPEN' as const,
        enrolled: false,
        learnerCount: 89,
      }],
    },
  ],
}

function LocationProbe() {
  const location = useLocation()
  return <output aria-label="location">{location.pathname}{location.search}</output>
}

describe('CommunityDiscoverPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.mocked(fetchCourseCatalog).mockReset().mockResolvedValue(catalog)
    vi.mocked(joinDiscoveredCohort).mockReset().mockResolvedValue(undefined)
    vi.mocked(fetchPublicProfiles).mockReset().mockResolvedValue({
      profiles: [{ userId: 'instructor-1', displayName: 'Dr. Alex Johnson' }],
    })
  })

  it('renders real catalog data and opens the exact Course after joining', async () => {
    const user = userEvent.setup()
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    queryClient.setQueryData(['study-server-navigation', 'user-1', 'server-1'], { courses: [] })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/app/servers/server-1/community/discover']}>
          <CommunityDiscoverPage />
          <LocationProbe />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: 'MATH 201 - Linear Algebra' })).toBeVisible()
    expect(await screen.findByText('Dr. Alex Johnson')).toBeVisible()
    expect(screen.getByText('89 learners')).toBeVisible()
    expect(screen.queryByText('CS 101 - Intro to CS')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Join MATH 201 - Linear Algebra' }))

    expect(joinDiscoveredCohort).toHaveBeenCalledWith('cohort-open')
    await waitFor(() => {
      expect(screen.getByLabelText('location')).toHaveTextContent(
        '/app/servers/server-1/courses/course-open/overview?cohort=cohort-open',
      )
    })
    expect(queryClient.getQueryState(['study-server-navigation', 'user-1', 'server-1'])?.isInvalidated)
      .toBe(true)
  })

  it('submits invite codes and exposes only truthful unavailable states', async () => {
    const user = userEvent.setup()
    let completeJoin: (() => void) | undefined
    vi.mocked(joinDiscoveredCohort).mockImplementation(() => new Promise((resolve) => {
      completeJoin = resolve
    }))
    vi.mocked(fetchCourseCatalog).mockResolvedValue({
      courses: [
        {
          id: 'course-enrolled',
          title: 'CS 101 - Intro to CS',
          instructorUserId: 'instructor-1',
          cohorts: [{
            id: 'cohort-enrolled',
            name: 'Spring cohort',
            enrollmentPolicy: 'OPEN',
            enrolled: true,
            learnerCount: 24,
          }],
        },
        {
          id: 'course-invite',
          title: 'MATH 201 - Linear Algebra',
          instructorUserId: 'instructor-1',
          cohorts: [{
            id: 'cohort-invite',
            name: 'Fall cohort',
            enrollmentPolicy: 'INVITE_ONLY',
            enrolled: false,
            learnerCount: 89,
          }],
        },
        {
          id: 'course-soon',
          title: 'ECON 210 - Microeconomics',
          instructorUserId: 'instructor-1',
          cohorts: [{
            id: 'cohort-soon',
            name: 'Winter cohort',
            enrollmentPolicy: 'OPENING_SOON',
            enrolled: false,
            learnerCount: 56,
          }],
        },
      ],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/app/servers/server-1/community/discover']}>
          <CommunityDiscoverPage />
          <LocationProbe />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const openingCard = (await screen.findByRole('heading', { name: 'ECON 210 - Microeconomics' }))
      .closest('article')
    expect(openingCard).not.toBeNull()
    expect(within(openingCard!).getByText('Opening soon')).toBeVisible()
    expect(within(openingCard!).queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByText('Request access')).not.toBeInTheDocument()
    expect(screen.queryByText('Apply to become an instructor')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open course' })).toHaveAttribute(
      'href',
      '/app/servers/server-1/courses/course-enrolled/overview?cohort=cohort-enrolled',
    )

    await user.click(screen.getByRole('button', { name: 'Enter invite code' }))
    const dialog = screen.getByRole('dialog', { name: 'Join MATH 201 - Linear Algebra' })
    await user.type(within(dialog).getByLabelText('Invite code'), 'invite-123')
    await user.click(within(dialog).getByRole('button', { name: 'Join Cohort' }))

    expect(joinDiscoveredCohort).toHaveBeenCalledWith('cohort-invite', 'invite-123')
    expect(within(dialog).getByRole('button', { name: 'Close invite code' })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.keyboard('{Escape}')
    expect(dialog).toBeInTheDocument()
    completeJoin?.()
    await waitFor(() => {
      expect(screen.getByLabelText('location')).toHaveTextContent(
        '/app/servers/server-1/courses/course-invite/overview?cohort=cohort-invite',
      )
    })
  })

  it('sends search and filter changes to the backend catalog', async () => {
    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CommunityDiscoverPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await screen.findByRole('heading', { name: 'MATH 201 - Linear Algebra' })
    await user.click(screen.getByRole('button', { name: 'Open' }))
    await waitFor(() => {
      expect(fetchCourseCatalog).toHaveBeenCalledWith('server-1', { search: '', filter: 'OPEN' })
    })
    await user.type(screen.getByRole('textbox', { name: 'Search courses in this hub' }), 'linear')
    await waitFor(() => {
      expect(fetchCourseCatalog).toHaveBeenLastCalledWith(
        'server-1',
        { search: 'linear', filter: 'OPEN' },
      )
    })
  })

  it('recovers from a catalog error through the visible retry action', async () => {
    const user = userEvent.setup()
    vi.mocked(fetchCourseCatalog).mockRejectedValueOnce(new Error('catalog unavailable'))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CommunityDiscoverPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load Courses')
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'MATH 201 - Linear Algebra' })).toBeVisible()
  })

  it('closes invite entry with Escape and restores focus to its trigger', async () => {
    const user = userEvent.setup()
    vi.mocked(fetchCourseCatalog).mockResolvedValue({
      courses: [{
        id: 'course-invite',
        title: 'MATH 201 - Linear Algebra',
        instructorUserId: 'instructor-1',
        cohorts: [{
          id: 'cohort-invite',
          name: 'Fall cohort',
          enrollmentPolicy: 'INVITE_ONLY',
          enrolled: false,
          learnerCount: 89,
        }],
      }],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CommunityDiscoverPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const trigger = await screen.findByRole('button', { name: 'Enter invite code' })
    await user.click(trigger)
    expect(screen.getByLabelText('Invite code')).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
