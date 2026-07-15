import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { BillingSettingsPage } from './BillingSettingsPage'

const mocks = vi.hoisted(() => ({
  useAccessibleStudyServersQuery: vi.fn(),
  useV2SidebarData: vi.fn(),
  useInstructorDashboardPage: vi.fn(),
}))

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useAccessibleStudyServersQuery: () => mocks.useAccessibleStudyServersQuery(),
}))

vi.mock('../hooks/use-v2-sidebar-data', () => ({
  useV2SidebarData: () => mocks.useV2SidebarData(),
}))

vi.mock('../../instructor-dashboard/hooks/use-instructor-dashboard-page', () => ({
  useInstructorDashboardPage: (...args: unknown[]) => mocks.useInstructorDashboardPage(...args),
}))

vi.mock('./HomePage', () => ({
  HomePage: () => <div data-testid="home-backdrop" />,
}))

function renderBilling() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/app/settings/billing']}>
        <Routes>
          <Route path="/app/settings/billing" element={<BillingSettingsPage />} />
          <Route path="/app/home" element={<p>Home redirected</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('BillingSettingsPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useAccessibleStudyServersQuery.mockReturnValue({
      isLoading: false,
      data: [{ id: 'server-1', name: 'Real Hub', owner: true }],
    })
    mocks.useV2SidebarData.mockReturnValue({
      isLoading: false,
      showBillingNav: true,
      serverGroups: [],
      allCourses: [],
    })
    mocks.useInstructorDashboardPage.mockReturnValue({
      servers: [{ id: 'server-1', name: 'Real Hub' }],
      selectedServerId: 'server-1',
      setSelectedServerId: vi.fn(),
      dashboard: {
        planTier: 'PRO',
        aiInvocationCount: 42,
        aiInvocationLimit: 100,
        remainingAiInvocations: 58,
        quotaExhausted: false,
      },
      planTierDraft: 'PRO',
      setPlanTierDraft: vi.fn(),
      isLoading: false,
      isUpdatingPlan: false,
      error: null,
      actionMessage: null,
      savePlan: vi.fn(),
    })
  })

  it('redirects non-owners away from billing', async () => {
    mocks.useV2SidebarData.mockReturnValue({
      isLoading: false,
      showBillingNav: false,
      serverGroups: [],
      allCourses: [],
    })
    mocks.useAccessibleStudyServersQuery.mockReturnValue({
      isLoading: false,
      data: [{ id: 'server-1', name: 'Real Hub', owner: false }],
    })

    renderBilling()
    expect(await screen.findByText('Home redirected')).toBeVisible()
  })

  it('shows real plan and AI usage without fake invoices or storage', async () => {
    const user = userEvent.setup()
    const savePlan = vi.fn()
    const setPlanTierDraft = vi.fn()
    mocks.useInstructorDashboardPage.mockReturnValue({
      servers: [{ id: 'server-1', name: 'Real Hub' }],
      selectedServerId: 'server-1',
      setSelectedServerId: vi.fn(),
      dashboard: {
        planTier: 'PRO',
        aiInvocationCount: 42,
        aiInvocationLimit: 100,
        remainingAiInvocations: 58,
        quotaExhausted: false,
      },
      planTierDraft: 'ORGANIZATION',
      setPlanTierDraft,
      isLoading: false,
      isUpdatingPlan: false,
      error: null,
      actionMessage: null,
      savePlan,
    })

    renderBilling()

    expect(await screen.findByRole('heading', { name: 'Plan and Billing' })).toBeVisible()
    expect(screen.getAllByText('Real Hub').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Pro').length).toBeGreaterThan(0)
    expect(screen.getByText(/42 of 100 queries used/)).toBeVisible()
    expect(screen.queryByText(/Invoice history/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/45 GB of 50 GB/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/\$29/)).not.toBeInTheDocument()
    expect(screen.getByText(/does not charge a card/i)).toBeVisible()
    expect(screen.getByText(/Storage metering and invoices are not available/i)).toBeVisible()

    await user.click(screen.getByRole('button', { name: /Save plan change/i }))
    await waitFor(() => expect(savePlan).toHaveBeenCalled())
  })
})
