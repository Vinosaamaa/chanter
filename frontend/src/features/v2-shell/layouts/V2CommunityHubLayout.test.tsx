import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchCourseCatalog } from '../../course-discovery/course-discovery-api'

import { V2CommunityHubLayout } from './V2CommunityHubLayout'

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useStudyServerNavigationQuery: () => ({
    data: {
      studyServerName: 'Real Study Hub',
      courses: [{ id: 'enrolled-course' }],
      capabilities: { owner: false, canManageCommunity: false },
    },
  }),
}))

vi.mock('../../community-members/community-members-api', async () => {
  const actual = await vi.importActual('../../community-members/community-members-api')
  return {
    ...actual,
    fetchStudyServerMemberSummary: vi.fn().mockResolvedValue({
      memberCount: 12,
      preview: [{ userId: 'u1', displayName: 'Sam' }],
    }),
    createStudyServerInvitations: vi.fn(),
  }
})

vi.mock('../../course-discovery/course-discovery-api', async () => {
  const actual = await vi.importActual('../../course-discovery/course-discovery-api')
  return { ...actual, fetchCourseCatalog: vi.fn() }
})

describe('V2CommunityHubLayout', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.mocked(fetchCourseCatalog).mockReset().mockResolvedValue({
      courses: [
        { id: 'course-1', title: 'Course 1', instructorUserId: 'user-1', cohorts: [] },
        { id: 'course-2', title: 'Course 2', instructorUserId: 'user-2', cohorts: [] },
      ],
    })
  })

  it('reports the full published catalog count instead of the accessible navigation count', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/app/servers/server-1/community/discover']}>
          <Routes>
            <Route path="/app/servers/:serverId/community/:tab" element={<V2CommunityHubLayout />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText(/2 courses/)).toBeVisible()
    expect(await screen.findByText(/12 members/)).toBeVisible()
    expect(fetchCourseCatalog).toHaveBeenCalledWith('server-1', { search: '', filter: 'ALL' })
  })
})
