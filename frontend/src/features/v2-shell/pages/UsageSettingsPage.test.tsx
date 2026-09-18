import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Navigate, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { UsageSettingsPage } from './UsageSettingsPage'

const mocks = vi.hoisted(() => ({ servers: vi.fn(), sidebar: vi.fn(), dashboard: vi.fn() }))
vi.mock('../../shell/hooks/use-shell-queries', () => ({ useAccessibleStudyServersQuery: () => mocks.servers() }))
vi.mock('../hooks/use-v2-sidebar-data', () => ({ useV2SidebarData: () => mocks.sidebar() }))
vi.mock('../../instructor-dashboard/hooks/use-instructor-dashboard-page', () => ({
  useInstructorDashboardPage: (...args: unknown[]) => mocks.dashboard(...args),
}))

function renderUsage() {
  return render(<MemoryRouter initialEntries={['/app/settings/billing']}><Routes>
    <Route path="/app/settings/billing" element={<Navigate to="/app/settings/usage" replace />} />
    <Route path="/app/settings/usage" element={<UsageSettingsPage />} />
    <Route path="/app/home" element={<p>Home redirected</p>} />
  </Routes></MemoryRouter>)
}

describe('UsageSettingsPage', () => {
  afterEach(cleanup)
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.servers.mockReturnValue({ isLoading: false, data: [{ id: 'server-1', name: 'Real Hub', owner: true }] })
    mocks.sidebar.mockReturnValue({ isLoading: false, showBillingNav: true })
    mocks.dashboard.mockReturnValue({
      servers: [{ id: 'server-1', name: 'Real Hub' }], selectedServerId: 'server-1',
      setSelectedServerId: vi.fn(), isOwner: true, isLoading: false, error: null,
      dashboard: { planTier: 'FREE_BETA', aiInvocationCount: 42, aiInvocationLimit: 100,
        remainingAiInvocations: 58, quotaExhausted: false },
    })
  })

  it('shows actual lifetime usage without a mutable plan or billing controls', async () => {
    renderUsage()
    expect(await screen.findByRole('heading', { name: 'Usage', level: 1 })).toBeVisible()
    expect(screen.getByText('Free beta')).toBeVisible()
    expect(screen.getByText(/42 of 100 assistant runs/)).toBeVisible()
    expect(screen.getByText(/does not reset monthly/i)).toBeVisible()
    expect(screen.queryByRole('combobox', { name: /plan/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save|upgrade|checkout|plan/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/invoice|card|\$29/i)).not.toBeInTheDocument()
  })

  it('redirects non-owners', async () => {
    mocks.servers.mockReturnValue({ isLoading: false, data: [{ id: 'server-1', name: 'Real Hub', owner: false }] })
    mocks.sidebar.mockReturnValue({ isLoading: false, showBillingNav: false })
    renderUsage()
    expect(await screen.findByText('Home redirected')).toBeVisible()
  })

  it('provides a route back home from the old billing link', async () => {
    renderUsage()
    await userEvent.click(await screen.findByRole('link', { name: 'Back to Home' }))
    expect(await screen.findByText('Home redirected')).toBeVisible()
  })

  it('does not turn missing usage into a fake zero count', async () => {
    mocks.dashboard.mockReturnValue({ isOwner: true, isLoading: true, dashboard: null, error: null })
    renderUsage()
    expect(await screen.findByRole('status')).toHaveTextContent('Loading usage')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.queryByText(/0 of/)).not.toBeInTheDocument()
  })
})
