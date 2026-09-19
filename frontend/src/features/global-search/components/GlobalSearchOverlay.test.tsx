import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { GlobalSearchProvider } from '../context/GlobalSearchProvider'
import { useGlobalSearch } from '../hooks/use-global-search'
import { GlobalSearchOverlay } from './GlobalSearchOverlay'

const searchApi = vi.hoisted(() => ({
  searchStudyServer: vi.fn(),
  reindexStudyServer: vi.fn(),
}))

vi.mock('../global-search-api', () => searchApi)

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useStudyServerNavigationQuery: () => ({
    data: {
      courses: [{
        id: 'course-real',
        title: 'CS 101',
        capabilities: { canUploadResources: false },
        cohorts: [],
        channels: [{ id: 'resources-channel', name: 'resources' }],
      }],
    },
    isError: false,
  }),
}))

describe('GlobalSearchOverlay v2', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    searchApi.searchStudyServer.mockResolvedValue({
      results: [
        {
          documentType: 'RESOURCE',
          courseId: 'course-real',
          courseTitle: 'CS 101',
          sourceId: 'resource-real',
          title: 'Recursion notes',
          snippet: 'Base cases and recursive calls',
        },
        {
          documentType: 'RESOURCE',
          courseId: 'course-hidden',
          courseTitle: 'Private Course',
          sourceId: 'resource-hidden',
          title: 'Private recursion notes',
          snippet: 'Must not be rendered',
        },
      ],
    })
  })

  it('scopes results to accessible Courses and deep-links to the v2 workspace', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/app/servers/server-real/courses/course-real/questions']}>
        <Routes>
          <Route path="/app/servers/:serverId/courses/:courseId/*" element={<SearchHarness />} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: 'Open search' }))
    expect(screen.getByRole('combobox', { name: 'Course' })).toBeDisabled()
    await user.type(screen.getByRole('textbox', { name: /search resources/i }), 'recursion')

    expect(await screen.findByText('Recursion notes')).toBeInTheDocument()
    expect(screen.queryByText('Private recursion notes')).not.toBeInTheDocument()
    expect(searchApi.searchStudyServer).toHaveBeenCalledWith('server-real', 'recursion', { courseId: 'course-real', documentType: undefined })

    await user.click(screen.getByText('Recursion notes'))
    await waitFor(() => {
      expect(screen.getByTestId('search-location')).toHaveTextContent(
        '/app/servers/server-real/courses/course-real/resources',
      )
    })
  })

  it('shows automatically indexed community results and opens their source', async () => {
    searchApi.searchStudyServer.mockResolvedValue({ results: [{
      documentType: 'ANNOUNCEMENT', courseId: null, courseTitle: 'Study community',
      sourceId: 'announcement-real', title: 'Welcome week', snippet: 'Meet your study group',
      href: '/app/servers/server-real/community/announcements',
    }] })
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/app/servers/server-real/home']}>
      <Routes><Route path="/app/servers/:serverId/*" element={<SearchHarness />} /></Routes>
    </MemoryRouter>)
    await user.click(screen.getByRole('button', { name: 'Open search' }))
    await user.type(screen.getByRole('textbox', { name: /search resources/i }), 'welcome')
    expect(await screen.findByText('Welcome week')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Refresh index' })).not.toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Content' }), 'ANNOUNCEMENT')
    await waitFor(() => expect(searchApi.searchStudyServer).toHaveBeenLastCalledWith('server-real', 'welcome', {
      documentType: 'ANNOUNCEMENT', courseId: undefined,
    }))
    await user.click(screen.getByText('Welcome week'))
    expect(screen.getByTestId('search-location')).toHaveTextContent('/app/servers/server-real/community/announcements')
  })

  it('refreshes an open unchanged query when durable indexing catches up', async () => {
    vi.useFakeTimers()
    searchApi.searchStudyServer.mockResolvedValueOnce({ results: [] }).mockResolvedValue({ results: [{
      documentType: 'EVENT', courseId: null, courseTitle: 'Community', sourceId: 'event-new',
      title: 'Newly indexed event', snippet: 'Just published', href: '/app/servers/server-real/community/events',
    }] })
    try {
      render(<MemoryRouter initialEntries={['/app/servers/server-real/home']}>
        <Routes><Route path="/app/servers/:serverId/*" element={<SearchHarness />} /></Routes>
      </MemoryRouter>)
      fireEvent.click(screen.getByRole('button', { name: 'Open search' }))
      fireEvent.change(screen.getByRole('textbox'), { target: { value: 'event' } })
      await act(() => vi.advanceTimersByTimeAsync(250))
      expect(screen.queryByText('Newly indexed event')).not.toBeInTheDocument()
      await act(() => vi.advanceTimersByTimeAsync(5000))
      expect(screen.getByText('Newly indexed event')).toBeInTheDocument()
    } finally { vi.useRealTimers() }
  })
})

function SearchHarness() {
  return <GlobalSearchProvider><SearchControls /></GlobalSearchProvider>
}

function SearchControls() {
  const { openSearch } = useGlobalSearch()
  const location = useLocation()

  return (
    <>
      <button type="button" onClick={openSearch}>Open search</button>
      <GlobalSearchOverlay variant="v2" />
      <p data-testid="search-location">{location.pathname}</p>
    </>
  )
}
