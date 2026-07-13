import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { HomePage } from './HomePage'

vi.mock('../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { displayName: string } }) => unknown) =>
    selector({ user: { displayName: 'Sam Learner' } }),
}))

vi.mock('../hooks/use-v2-sidebar-data', () => ({
  useV2SidebarData: () => ({
    isLoading: false,
    isError: false,
    showTeachingNav: false,
    serverGroups: [],
    allCourses: [
      {
        id: 'c1',
        serverId: 's1',
        serverName: 'Spring Bootcamp Hub',
        title: 'CS 101 — Intro to CS',
        cohortLabel: 'Spring cohort',
        accentColor: '#3b82f6',
        unreadCount: 3,
      },
    ],
  }),
}))

describe('HomePage', () => {
  it('renders greeting and continue learning section', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Good')
    expect(screen.getByText('Continue learning')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /CS 101 — Intro to CS/i })).toBeInTheDocument()
    expect(screen.getByText('Up next')).toBeInTheDocument()
  })
})
