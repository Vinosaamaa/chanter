import { act, cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { AuthSessionBootstrap } from './AuthSessionBootstrap'
import { useAuthStore } from '../stores/auth-store'

vi.mock('../features/auth/browser-session', () => ({
  restoreBrowserSession: vi.fn(() => new Promise(() => {})),
  synchronizeBrowserSession: vi.fn(() => () => {}),
}))
beforeEach(() => useAuthStore.setState({ status: 'restoring', accessToken: null, user: null }))
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
