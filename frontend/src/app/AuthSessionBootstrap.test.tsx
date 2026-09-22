import { act, cleanup, render, screen } from '@testing-library/react'
import { Link, MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { AuthSessionBootstrap } from './AuthSessionBootstrap'
import { useAuthStore } from '../stores/auth-store'
import { restoreBrowserSession } from '../features/auth/browser-session'

vi.mock('../features/auth/browser-session', () => ({
  restoreBrowserSession: vi.fn(() => new Promise(() => {})),
  synchronizeBrowserSession: vi.fn(() => () => {}),
}))
beforeEach(() => { vi.clearAllMocks(); useAuthStore.setState({ status: 'restoring', accessToken: null, user: null }) })
afterEach(cleanup)

it('keeps public information available while the session service is unavailable', () => {
  useAuthStore.setState({ status: 'unavailable' })
  render(<MemoryRouter initialEntries={['/privacy']}><AuthSessionBootstrap><h1>Privacy</h1></AuthSessionBootstrap></MemoryRouter>)
  expect(screen.getByRole('heading', { name: 'Privacy' })).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('holds authenticated content until restoration completes', () => {
  render(<MemoryRouter initialEntries={['/app/home']}><AuthSessionBootstrap><h1>My courses</h1></AuthSessionBootstrap></MemoryRouter>)
  expect(screen.getByRole('status')).toHaveTextContent('Restoring your session')
  expect(screen.queryByRole('heading', { name: 'My courses' })).not.toBeInTheDocument()
  act(() => useAuthStore.setState({ status: 'ready' }))
  expect(screen.getByRole('heading', { name: 'My courses' })).toBeInTheDocument()
})

it('opens deletion receipts without refreshing a revoked session but restores when leaving for sign-in', async () => {
  render(<MemoryRouter initialEntries={['/account-deletion/b633c892-6762-40ec-a945-b042957a052b']}><AuthSessionBootstrap>
    <h1>Deletion status</h1><Link to="/sign-in">Manage preparation</Link>
  </AuthSessionBootstrap></MemoryRouter>)
  expect(screen.getByRole('heading', { name: 'Deletion status' })).toBeVisible()
  expect(restoreBrowserSession).not.toHaveBeenCalled()
  await userEvent.setup().click(screen.getByRole('link', { name: 'Manage preparation' }))
  expect(restoreBrowserSession).toHaveBeenCalledTimes(1)
  expect(screen.getByRole('status')).toHaveTextContent('Restoring your session')
})
