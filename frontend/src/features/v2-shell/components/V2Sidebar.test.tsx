import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import type { V2SidebarData } from '../hooks/use-v2-sidebar-data'
import { V2Sidebar } from './V2Sidebar'

const authApi = vi.hoisted(() => ({ logout: vi.fn() }))
vi.mock('../../auth/auth-api', () => authApi)

const sidebarData: V2SidebarData = {
  isLoading: false,
  isError: false,
  showTeachingNav: false,
  showBillingNav: false,
  serverGroups: [
    {
      id: 'server-1',
      name: 'STUDY SERVER',
      expanded: true,
      courses: [
        {
          id: 'course-1',
          serverId: 'server-1',
          serverName: 'Study Server',
          title: 'CS 101',
          cohortLabel: 'Spring cohort',
          accentColor: '#3b82f6',
        },
      ],
    },
  ],
  allCourses: [],
}

describe('V2Sidebar account menu', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    authApi.logout.mockResolvedValue(undefined)
    useAuthStore.setState({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 'user-1', email: 'sam@example.com', displayName: 'Sam Lee' },
    })
  })

  it('does not show a synthetic Inbox unread badge', () => {
    render(
      <MemoryRouter initialEntries={['/app/home']}>
        <V2Sidebar data={sidebarData} menuOpen={false} onCloseMenu={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Inbox' })).toBeInTheDocument()
    expect(screen.queryByText('4')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'CS 101' })).toBeInTheDocument()
    expect(screen.queryByText('3')).not.toBeInTheDocument()
  })

  it('signs out from the profile menu and clears the local session', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/app/home']}>
        <V2Sidebar data={sidebarData} menuOpen={false} onCloseMenu={vi.fn()} />
        <LocationProbe />
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: 'Open account menu' }))
    expect(screen.getByRole('menu', { name: 'Account' })).toBeInTheDocument()

    await user.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    await waitFor(() => expect(screen.getByTestId('sidebar-location')).toHaveTextContent('/sign-in'))
    expect(authApi.logout).toHaveBeenCalledWith('refresh-token')
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().refreshToken).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
  })
})

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="sidebar-location">{location.pathname}</p>
}
