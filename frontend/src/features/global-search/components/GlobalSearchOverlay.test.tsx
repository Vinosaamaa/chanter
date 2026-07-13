import { cleanup, render, screen, waitFor } from '@testing-library/react'
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
    expect(searchApi.searchStudyServer).toHaveBeenCalledWith('server-real', 'recursion')

    await user.click(screen.getByText('Recursion notes'))
    await waitFor(() => {
      expect(screen.getByTestId('search-location')).toHaveTextContent(
        '/app/servers/server-real/courses/course-real/resources',
      )
    })
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
