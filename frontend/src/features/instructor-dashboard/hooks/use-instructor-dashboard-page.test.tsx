import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../lib/api-client'
import { useInstructorDashboardPage } from './use-instructor-dashboard-page'

const mocks = vi.hoisted(() => ({ dashboard: vi.fn(), servers: {
  isLoading: false, isError: false, error: null as unknown,
  data: [{ id: 'server-1' }], refetch: vi.fn(),
} }))
vi.mock('../../../stores/auth-store', () => ({ useAuthStore: () => 'owner-1' }))
vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useAccessibleStudyServersQuery: () => mocks.servers,
}))

beforeEach(() => {
  mocks.servers.isError = false
  mocks.servers.error = null
  mocks.servers.refetch.mockReset()
})

it('reports server-list failure and retries that authority before requesting any cached or bookmarked server', async () => {
  mocks.dashboard.mockReset()
  mocks.servers.isError = true
  mocks.servers.error = new ApiError('Request failed', 502)
  mocks.servers.refetch.mockResolvedValue({ isError: true })
  const select = vi.fn()
  const { result, rerender } = renderHook(() => useInstructorDashboardPage('removed-server', select))
  expect(result.current.error).toBe('Usage and teaching information are temporarily unavailable. Please try again.')
  expect(result.current.dashboard).toBeNull()
  expect(result.current.selectedServerId).toBeNull()
  expect(result.current.isOwner).toBe(false)
  await act(() => result.current.refresh())
  expect(mocks.servers.refetch).toHaveBeenCalledTimes(1)
  expect(mocks.dashboard).not.toHaveBeenCalled()
  expect(select).not.toHaveBeenCalled()
  mocks.servers.isError = false
  mocks.servers.error = null
  mocks.dashboard.mockResolvedValue({ studyServerId: 'server-1' })
  rerender()
  await waitFor(() => expect(result.current.dashboard).toEqual({ studyServerId: 'server-1' }))
  expect(mocks.dashboard).toHaveBeenCalledWith('server-1')
  expect(mocks.dashboard).not.toHaveBeenCalledWith('removed-server')
})
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

it('replaces an inaccessible server bookmark before requesting its dashboard', async () => {
  mocks.dashboard.mockReset().mockResolvedValue({ studyServerId: 'server-1' })
  const select = vi.fn()
  const { result } = renderHook(() => useInstructorDashboardPage('removed-server', select))
  await waitFor(() => expect(result.current.isLoading).toBe(false))
  expect(select).toHaveBeenCalledWith('server-1')
  expect(mocks.dashboard).toHaveBeenCalledWith('server-1')
  expect(mocks.dashboard).not.toHaveBeenCalledWith('removed-server')
  expect(result.current.selectedServerId).toBe('server-1')
})
