import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { V2TopBar } from './V2TopBar'

const navigation = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useStudyServerNavigationQuery: () => navigation.value,
}))

describe('V2TopBar', () => {
  afterEach(cleanup)

  it('focuses route-scoped search with Command-F', () => {
    render(
      <MemoryRouter initialEntries={['/app/inbox']}>
        <V2TopBar onOpenMenu={vi.fn()} />
      </MemoryRouter>,
    )

    const search = screen.getByRole('searchbox', { name: /search inbox/i })
    expect(screen.getByText('⌘F')).toBeInTheDocument()

    fireEvent.keyDown(window, { key: 'f', metaKey: true })

    expect(search).toHaveFocus()
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

    render(
      <MemoryRouter initialEntries={['/app/servers/server-real/courses/course-real/questions?cohort=cohort-fall']}>
        <V2TopBar onOpenMenu={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent(
      'Spring Bootcamp Hub / CS 101 / Fall 2026',
    )
  })
})
