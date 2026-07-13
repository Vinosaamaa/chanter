import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { V2TopBar } from './V2TopBar'
import { GlobalSearchProvider } from '../../global-search/context/GlobalSearchProvider'
import { useGlobalSearch } from '../../global-search/hooks/use-global-search'

const navigation = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useStudyServerNavigationQuery: () => navigation.value,
}))

describe('V2TopBar', () => {
  afterEach(cleanup)

  it('opens real route-scoped search from the top bar', () => {
    renderTopBar('/app/inbox')

    const search = screen.getByRole('searchbox', { name: /search inbox/i })
    expect(screen.getByText('⌘F')).toBeInTheDocument()

    fireEvent.click(search)

    expect(screen.getByTestId('search-state')).toHaveTextContent('open')
  })

  it('shows the real selected cohort in a course breadcrumb', () => {
    navigation.value = {
      data: {
        studyServerName: 'Spring Bootcamp Hub',
        courses: [{
          id: 'course-real',
          title: 'CS 101',
          cohorts: [
            { id: 'cohort-spring', name: 'Spring 2026' },
            { id: 'cohort-fall', name: 'Fall 2026' },
          ],
        }],
      },
    }

    renderTopBar('/app/servers/server-real/courses/course-real/questions?cohort=cohort-fall')

    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent(
      'Spring Bootcamp Hub / CS 101 / Fall 2026',
    )
  })
})

function renderTopBar(entry: string) {
  render(
    <MemoryRouter initialEntries={[entry]}>
      <GlobalSearchProvider>
        <V2TopBar onOpenMenu={vi.fn()} />
        <SearchStateProbe />
      </GlobalSearchProvider>
    </MemoryRouter>,
  )
}

function SearchStateProbe() {
  const { isOpen } = useGlobalSearch()
  return <p data-testid="search-state">{isOpen ? 'open' : 'closed'}</p>
}
