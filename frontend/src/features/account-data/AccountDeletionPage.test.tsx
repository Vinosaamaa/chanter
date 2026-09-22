import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import type { DeletionJob } from './account-deletion-api'
import { AccountDeletionPage, AccountDeletionReceiptPage } from './AccountDeletionPage'

const api = vi.hoisted(() => ({ prepareDeletion: vi.fn(), getDeletion: vi.fn(), cancelDeletion: vi.fn(), confirmDeletion: vi.fn(), getDeletionReceipt: vi.fn() }))
vi.mock('./account-deletion-api', async importOriginal => ({ ...await importOriginal<typeof import('./account-deletion-api')>(), ...api }))
const id = 'b633c892-6762-40ec-a945-b042957a052b'
const otherId = 'b633c892-6762-40ec-a945-b042957a052c'
const prepared: DeletionJob = { id, state: 'PREPARED', createdAt: new Date().toISOString(), preparationExpiresAt: new Date(Date.now() + 240_000).toISOString(), replicationPending: false, preparationError: null,
  parts: ['auth', 'community', 'message', 'media', 'agent', 'search', 'notification'].map(source => ({ source, state: 'PENDING', errorCode: null })) }
function session(account = 'owner') { useAuthStore.getState().setSession({ accessToken: `${account}-token`, expiresInSeconds: 900, user: { id: account, email: `${account}@example.test`, displayName: account } }) }
function open(path = '/app/account-data/delete') {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })}><MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/app/account-data/delete" element={<><Link to={`/app/account-data/delete?job=${otherId}`}>Another request</Link><AccountDeletionPage /></>} />
    <Route path="/account-deletion/:jobId" element={<AccountDeletionReceiptPage />} />
  </Routes></MemoryRouter></QueryClientProvider>)
}
beforeEach(() => { vi.resetAllMocks(); session(); api.getDeletion.mockResolvedValue(prepared); api.prepareDeletion.mockImplementation(async (requestId: string) => ({ ...prepared, id: requestId, state: 'PREPARING' })) })
afterEach(() => cleanup())

it('preparation never claims deletion and an uncertain request reuses its UUID', async () => {
  api.prepareDeletion.mockRejectedValueOnce(new Error('connection lost'))
  api.getDeletion.mockRejectedValue(new ApiError('missing', 404))
  const user = userEvent.setup(); open()
  await user.click(screen.getByRole('button', { name: 'Prepare deletion' }))
  await screen.findByText(/could not verify preparation/i)
  await user.click(await screen.findByRole('button', { name: 'Retry preparation' }))
  expect(await screen.findByText('Checking ownership')).toBeVisible()
  expect(api.prepareDeletion.mock.calls[0][0]).toBe(api.prepareDeletion.mock.calls[1][0])
  expect(useAuthStore.getState().user?.id).toBe('owner')
  expect(api.confirmDeletion).not.toHaveBeenCalled()
})

it('requires explicit confirmation then keeps receipt access after clearing the account', async () => {
  api.confirmDeletion.mockResolvedValue({ ...prepared, state: 'ERASING' })
  api.getDeletionReceipt.mockResolvedValue({ ...prepared, state: 'ERASING' })
  const user = userEvent.setup(); open(`/app/account-data/delete?job=${id}`)
  const confirm = await screen.findByRole('button', { name: 'Permanently delete my account' })
  expect(confirm).toBeDisabled()
  await user.type(screen.getByLabelText('Type DELETE MY ACCOUNT to confirm'), 'DELETE MY ACCOUNT')
  await user.click(confirm)
  expect(await screen.findByRole('heading', { name: 'Deletion status' })).toBeVisible()
  expect(await screen.findByText('Cleanup in progress')).toBeVisible()
  expect(useAuthStore.getState().user).toBeNull()
  expect(api.getDeletionReceipt).toHaveBeenCalledWith(id, expect.any(AbortSignal))
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
})

it.each([new TypeError('network lost'), new ApiError('unreadable accepted response', 202)])('checks the same receipt after an uncertain confirmation without creating another job: %s', async failure => {
  api.confirmDeletion.mockRejectedValue(failure)
  api.getDeletionReceipt.mockResolvedValue({ ...prepared, state: 'WAITING_FOR_REPLICA', replicationPending: true })
  const user = userEvent.setup(); open(`/app/account-data/delete?job=${id}`)
  await user.type(await screen.findByLabelText('Type DELETE MY ACCOUNT to confirm'), 'DELETE MY ACCOUNT')
  await user.click(screen.getByRole('button', { name: 'Permanently delete my account' }))
  expect(await screen.findByText('Recovery acknowledgement pending')).toBeVisible()
  expect(api.prepareDeletion).not.toHaveBeenCalled()
  expect(api.confirmDeletion).toHaveBeenCalledTimes(1)
})

it('ignores an old account preparation response and aborts it after an account switch', async () => {
  let resolve!: (job: DeletionJob) => void
  api.prepareDeletion.mockReturnValue(new Promise<DeletionJob>(done => { resolve = done }))
  const user = userEvent.setup(); open()
  await user.click(screen.getByRole('button', { name: 'Prepare deletion' }))
  const signal = api.prepareDeletion.mock.calls[0][1] as AbortSignal
  await act(async () => session('other'))
  await act(async () => resolve(prepared))
  expect(signal.aborted).toBe(true)
  expect(screen.queryByText('Ready to confirm')).not.toBeInTheDocument()
  expect(api.getDeletion).not.toHaveBeenCalled()
})

it('never treats a missing receipt as completion and provides an explicit refresh', async () => {
  useAuthStore.getState().clearSession()
  api.getDeletionReceipt.mockRejectedValueOnce(new ApiError('gone', 404)).mockResolvedValue({ ...prepared, state: 'COMPLETE', parts: prepared.parts.map(part => ({ ...part, state: 'PRESERVED' })) })
  const user = userEvent.setup(); open(`/account-deletion/${id}`)
  expect(await screen.findByRole('alert')).toHaveTextContent('Receipt unavailable')
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Refresh status' }))
  expect(await screen.findByText('Deletion completed')).toBeVisible()
  expect(screen.getByText(/Restricted moderation records may remain/)).toBeVisible()
  expect(api.getDeletion).not.toHaveBeenCalled()
})

it('does not allow confirmation while cancellation is in flight', async () => {
  api.cancelDeletion.mockReturnValue(new Promise(() => {}))
  const user = userEvent.setup(); open(`/app/account-data/delete?job=${id}`)
  await user.type(await screen.findByLabelText('Type DELETE MY ACCOUNT to confirm'), 'DELETE MY ACCOUNT')
  await user.click(screen.getByRole('button', { name: 'Cancel preparation' }))
  expect(screen.getByRole('button', { name: 'Permanently delete my account' })).toBeDisabled()
  expect(api.confirmDeletion).not.toHaveBeenCalled()
})

it('ignores an old confirmation after navigating to another request and retries the new UUID', async () => {
  let resolve!: (job: DeletionJob) => void
  api.confirmDeletion.mockReturnValue(new Promise<DeletionJob>(done => { resolve = done }))
  api.getDeletion.mockImplementation(async (request: string) => {
    if (request === otherId) throw new ApiError('missing', 404)
    return prepared
  })
  const user = userEvent.setup(); open(`/app/account-data/delete?job=${id}`)
  await user.type(await screen.findByLabelText('Type DELETE MY ACCOUNT to confirm'), 'DELETE MY ACCOUNT')
  await user.click(screen.getByRole('button', { name: 'Permanently delete my account' }))
  const signal = api.confirmDeletion.mock.calls[0][1] as AbortSignal
  await user.click(screen.getByRole('link', { name: 'Another request' }))
  await act(async () => resolve({ ...prepared, state: 'ERASING' }))
  expect(signal.aborted).toBe(true)
  expect(useAuthStore.getState().user?.id).toBe('owner')
  expect(api.getDeletionReceipt).not.toHaveBeenCalled()
  await user.click(await screen.findByRole('button', { name: 'Retry preparation' }))
  expect(api.prepareDeletion).toHaveBeenCalledWith(otherId, expect.any(AbortSignal))
})

it('disables irreversible confirmation for an expired preparation', async () => {
  api.getDeletion.mockResolvedValue({ ...prepared, preparationExpiresAt: new Date(Date.now() - 1000).toISOString() })
  open(`/app/account-data/delete?job=${id}`)
  expect(await screen.findByRole('button', { name: 'Permanently delete my account' })).toBeDisabled()
  expect(screen.getByLabelText('Type DELETE MY ACCOUNT to confirm')).toBeDisabled()
  expect(api.confirmDeletion).not.toHaveBeenCalled()
})

it('does not label a cached receipt current after its authority expires', async () => {
  api.getDeletionReceipt.mockResolvedValueOnce({ ...prepared, state: 'COMPLETE' }).mockRejectedValue(new ApiError('expired cookie', 404))
  const user = userEvent.setup(); open(`/account-deletion/${id}`)
  expect(await screen.findByText('Deletion completed')).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Refresh status' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Receipt unavailable')
  expect(screen.queryByRole('region', { name: 'Current deletion status' })).not.toBeInTheDocument()
})
