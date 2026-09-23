import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../../lib/api-client'
import { useAccessibleStudyServersQuery } from '../../shell/hooks/use-shell-queries'
import { useInstructorDashboardPage } from './use-instructor-dashboard-page'

const mocks = vi.hoisted(() => ({ userId: 'owner-1', servers: vi.fn(), dashboard: vi.fn() }))
vi.mock('../../../stores/auth-store', () => ({ useAuthStore: () => mocks.userId }))
vi.mock('../../shell/shell-api', () => ({ fetchAccessibleStudyServers: () => mocks.servers() }))
vi.mock('../instructor-dashboard-api', () => ({
  fetchInstructorDashboard: (id: string) => mocks.dashboard(id),
  fetchStudyServerDetails: async () => ({ ownerRole: { userId: mocks.userId } }),
}))
const select = () => undefined
function Dashboard() {
  const page = useInstructorDashboardPage('obsolete-bookmark', select)
  return <><p role="alert">{page.error}</p><output>{page.dashboard?.studyServerId}</output><button onClick={() => void page.refresh()}>Retry</button></>
}
function Parent() {
  const list = useAccessibleStudyServersQuery()
  return list.isLoading ? <p>Loading authority</p> : <Dashboard />
}
afterEach(cleanup)

it('does not retry merely by mounting the nested observer; explicit retry and account change use current authority', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 30_000 } } })
  let fail!: (error: Error) => void
  mocks.servers.mockReset().mockImplementationOnce(() => new Promise((_, reject) => { fail = reject }))
    .mockResolvedValueOnce([{ id: 'server-1', name: 'First' }])
  mocks.dashboard.mockReset().mockImplementation(async (id: string) => ({ studyServerId: id }))
  const tree = <QueryClientProvider client={client}><Parent /></QueryClientProvider>
  const view = render(tree)
  try {
    await act(async () => fail(new ApiError('Unavailable', 502)))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Usage and teaching information are temporarily unavailable.'))
    expect(mocks.servers).toHaveBeenCalledTimes(1)
    expect(mocks.dashboard).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('server-1'))
    expect(mocks.servers).toHaveBeenCalledTimes(2)
    expect(mocks.dashboard).toHaveBeenCalledWith('server-1')

    let finish!: (servers: { id: string; name: string }[]) => void
    mocks.servers.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    mocks.userId = 'owner-2'
    view.rerender(<QueryClientProvider client={client}><Parent /></QueryClientProvider>)
    expect(screen.getByText('Loading authority')).toBeVisible()
    expect(screen.queryByText('server-1')).not.toBeInTheDocument()
    await act(async () => finish([{ id: 'server-2', name: 'Second' }]))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('server-2'))
    expect(mocks.servers).toHaveBeenCalledTimes(3)
    expect(mocks.dashboard).not.toHaveBeenCalledWith('obsolete-bookmark')
  } finally { view.unmount(); client.clear(); mocks.userId = 'owner-1' }
})
