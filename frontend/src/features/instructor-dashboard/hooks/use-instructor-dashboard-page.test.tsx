import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../lib/api-client'
import { useInstructorDashboardPage } from './use-instructor-dashboard-page'

const mocks = vi.hoisted(() => ({ dashboard: vi.fn() }))
vi.mock('../../../stores/auth-store', () => ({ useAuthStore: () => 'owner-1' }))
vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useAccessibleStudyServersQuery: () => ({ isLoading: false, data: [{ id: 'server-1' }] }),
}))
vi.mock('../instructor-dashboard-api', () => ({
  fetchInstructorDashboard: (...args: unknown[]) => mocks.dashboard(...args),
  fetchStudyServerDetails: async () => ({ ownerRole: { userId: 'owner-1' } }),
}))

describe('dashboard unavailability', () => {
  it.each(['', 'Bad Gateway', '{"error":"Bad Gateway"}'])('explains a 502 response with body %j', async body => {
    mocks.dashboard.mockRejectedValue(new ApiError('Request failed', 502, body))
    const selectServer = vi.fn()
    const { result } = renderHook(() => useInstructorDashboardPage('server-1', selectServer))
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.error).toBe('Usage and teaching information are temporarily unavailable. Please try again.')
    expect(result.current.dashboard).toBeNull()
  })
})
