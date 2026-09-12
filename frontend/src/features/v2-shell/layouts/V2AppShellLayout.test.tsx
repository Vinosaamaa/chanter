import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { V2AppShellLayout } from './V2AppShellLayout'

vi.mock('../hooks/use-v2-sidebar-data', () => ({
  useV2SidebarData: () => ({
    isLoading: false,
    isError: false,
    showTeachingNav: false,
    showBillingNav: false,
    serverGroups: [],
    allCourses: [],
  }),
}))

describe('V2AppShellLayout mobile navigation', () => {
  afterEach(cleanup)
  it('opens and closes the sidebar from the top bar controls', async () => {
    const user = userEvent.setup()
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/app/home']}>
          <Routes>
            <Route path="/app" element={<V2AppShellLayout />}>
              <Route path="home" element={<p>Home content</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const sidebar = screen.getByRole('complementary')
    expect(sidebar).not.toHaveClass('open')

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))
    expect(sidebar).toHaveClass('open')

    await user.click(within(sidebar).getByRole('button', { name: 'Close navigation' }))
    expect(sidebar).not.toHaveClass('open')

    await user.click(screen.getByRole('searchbox', { name: 'Search your courses' }))
    expect(screen.getByRole('dialog', { name: 'Global search' })).toBeInTheDocument()
  })

  it('lets a phone user browse courses, dismiss with Escape, and return to the trigger', async () => {
    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/app/home']}>
          <Routes>
            <Route path="/app" element={<V2AppShellLayout />}>
              <Route path="home" element={<p>Home content</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    const mobileNav = screen.getByRole('navigation', { name: 'Mobile navigation' })
    expect(within(mobileNav).getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')
    const browse = within(mobileNav).getByRole('button', { name: 'Browse' })
    await user.click(browse)
    expect(screen.getByRole('dialog', { name: 'Browse Chanter' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close navigation' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Browse Chanter' })).not.toBeInTheDocument()
    await waitFor(() => expect(browse).toHaveFocus())
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main-content')
  })
})
