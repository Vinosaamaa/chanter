import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { OperatorPage } from './OperatorPage'
const request = vi.hoisted(() => vi.fn())
vi.mock('../../lib/api-client', async importOriginal => ({ ...await importOriginal<object>(), apiFetch: request }))
vi.mock('../../stores/auth-store', () => ({ useAuthStore: (selector: (state: unknown) => unknown) => selector({ user: { id: 'operator' } }) }))
afterEach(() => { cleanup(); request.mockReset() })

it('shows denied operator access without requesting reports or evidence', async () => {
  request.mockRejectedValue(new Error('Denied'))
  render(<MemoryRouter><OperatorPage /></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Operator access could not be verified')
  expect(request).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('button', { name: 'Load reports' })).not.toBeInTheDocument()
})

it('requires verification and an investigation reason before reading a report queue', async () => {
  request.mockImplementation(async (path: string) => {
    if (path.endsWith('/verification')) return { userId: 'operator', role: 'ADMIN', enrolled: true }
    if (path.endsWith('/challenge')) return { token: 'temporary-verification', expiresAt: new Date(Date.now() + 300000).toISOString() }
    return []
  })
  render(<MemoryRouter><OperatorPage /></MemoryRouter>)
  await userEvent.type(await screen.findByLabelText('Account password'), 'correct password')
  await userEvent.type(screen.getByLabelText('Authenticator code'), '123456')
  await userEvent.click(screen.getByRole('button', { name: 'Verify operator access' }))
  expect(await screen.findByRole('button', { name: 'Load reports' })).toBeDisabled()
  await userEvent.type(screen.getByLabelText('Investigation reason'), 'Review assigned abuse cases')
  await userEvent.click(screen.getByRole('button', { name: 'Load reports' }))
  expect(await screen.findByText('No reports match this view.')).toBeVisible()
  expect(request).toHaveBeenLastCalledWith(expect.stringContaining('/api/v1/platform-admin/reports?'), expect.objectContaining({
    headers: { 'X-Chanter-Operator-Verification': 'temporary-verification' }, skipAuthRefresh: true,
  }))
})

it('does not reveal a late report response after the workspace is locked', async () => {
  let resolveQueue: (value: unknown[]) => void = () => { throw new Error('Queue was not requested') }
  request.mockImplementation((path: string) => {
    if (path.endsWith('/verification')) return Promise.resolve({ userId: 'operator', role: 'ADMIN', enrolled: true })
    if (path.endsWith('/challenge')) return Promise.resolve({ token: 'proof', expiresAt: new Date(Date.now() + 300000).toISOString() })
    return new Promise(resolve => { resolveQueue = resolve })
  })
  render(<MemoryRouter><OperatorPage /></MemoryRouter>)
  fireEvent.change(await screen.findByLabelText('Account password'), { target: { value: 'correct password' } })
  fireEvent.change(screen.getByLabelText('Authenticator code'), { target: { value: '123456' } })
  await userEvent.click(screen.getByRole('button', { name: 'Verify operator access' }))
  fireEvent.change(await screen.findByLabelText('Investigation reason'), { target: { value: 'Review assigned cases' } })
  await userEvent.click(screen.getByRole('button', { name: 'Load reports' }))
  await userEvent.click(screen.getByRole('button', { name: 'Lock workspace' }))
  await act(async () => resolveQueue([{ report: { id: 'late', targetType: 'USER', reason: 'Private late evidence', status: 'NEW', createdAt: new Date().toISOString() } }]))
  expect(screen.getByLabelText('Account password')).toBeVisible()
  expect(screen.queryByText('Private late evidence')).not.toBeInTheDocument()
  expect(screen.queryByRole('complementary', { name: 'Report queue' })).not.toBeInTheDocument()
})
