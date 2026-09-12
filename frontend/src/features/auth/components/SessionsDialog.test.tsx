import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { SessionsDialog } from './SessionsDialog'
import { useAuthStore } from '../../../stores/auth-store'

const authApi = vi.hoisted(() => ({ fetchSessions: vi.fn(), revokeSession: vi.fn(), logout: vi.fn() }))
vi.mock('../auth-api', () => authApi)

const current = { id: 'current', createdAt: '2026-09-10T12:00:00Z', lastUsedAt: '2026-09-11T12:00:00Z', expiresAt: '2026-09-17T12:00:00Z', userAgent: 'Mozilla/5.0 (Windows NT 10.0) Chrome/140.0.0.0', current: true }
const other = { ...current, id: 'other', userAgent: 'Mozilla/5.0 (iPhone) AppleWebKit Safari/605.1', current: false }

function openDialog() {
  const close = vi.fn()
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter><SessionsDialog onClose={close} /></MemoryRouter>
  </QueryClientProvider>)
  return close
}

describe('session settings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setSession({ accessToken: 'owner-token', expiresInSeconds: 900, user: { id: 'owner', email: 'owner@example.com', displayName: 'Owner' } })
    authApi.fetchSessions.mockResolvedValue({ sessions: [current, other] })
    authApi.revokeSession.mockResolvedValue(undefined)
    localStorage.clear()
    Object.defineProperty(navigator, 'locks', { configurable: true, value: {
      request: vi.fn((_name, callback) => Promise.resolve().then(callback)),
    } })
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', '') } })
    Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value() { this.removeAttribute('open') } })
  })
  afterEach(cleanup)

  it('lists devices, identifies this device, and removes a revoked device after the server confirms', async () => {
    const user = userEvent.setup()
    openDialog()
    expect(screen.getByRole('dialog', { name: 'Sessions and devices' })).toBeInTheDocument()
    expect(await screen.findByText('This device')).toBeInTheDocument()
    const otherDevice = screen.getByRole('listitem', { name: 'Safari on iPhone' })
    await user.click(within(otherDevice).getByRole('button', { name: 'Sign out Safari on iPhone' }))
    expect(authApi.revokeSession).toHaveBeenCalledWith('other')
    expect(await screen.findByRole('status')).toHaveTextContent('Safari on iPhone signed out')
    expect(screen.queryByRole('listitem', { name: 'Safari on iPhone' })).not.toBeInTheDocument()
    expect(screen.getByText('This device')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close session settings' })).toHaveFocus()
  })

  it('keeps a device visible and announces failed revocation', async () => {
    authApi.revokeSession.mockRejectedValue(new Error('Offline'))
    const user = userEvent.setup()
    openDialog()
    await user.click(await screen.findByRole('button', { name: 'Sign out Safari on iPhone' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not sign out this device')
    expect(screen.getByRole('listitem', { name: 'Safari on iPhone' })).toBeInTheDocument()
  })

  it('shows a retry control when the device list cannot load', async () => {
    authApi.fetchSessions.mockRejectedValueOnce(new Error('Offline'))
    const user = userEvent.setup()
    openDialog()
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your sessions')
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('This device')).toBeInTheDocument()
  })

  it('keeps keyboard focus within the device controls and closes before returning focus', async () => {
    const user = userEvent.setup()
    const close = openDialog()
    const lastButton = await screen.findByRole('button', { name: 'Sign out Safari on iPhone' })
    screen.getByRole('button', { name: 'Close session settings' }).focus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(lastButton).toHaveFocus()
    await user.keyboard('{Tab}')
    expect(screen.getByRole('button', { name: 'Close session settings' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Close session settings' }))
    expect(close).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
