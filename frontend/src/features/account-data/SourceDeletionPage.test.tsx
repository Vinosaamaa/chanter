import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { SourceDeletionPage } from './SourceDeletionPage'

const api = vi.hoisted(() => ({ getSourceDeletion: vi.fn() }))
vi.mock('./source-deletion-api', () => api)
const id = 'b633c892-6762-40ec-a945-b042957a052b'
const job = { jobId: id, targetKind: 'RESOURCE', targetId: id, state: 'ERASING', replicationPending: true, parts: [{ source: 'media', state: 'PENDING', errorCode: null }] }
function session(account = 'owner') { useAuthStore.getState().setSession({ accessToken: account, expiresInSeconds: 900, user: { id: account, email: `${account}@example.test`, displayName: account } }) }
function open() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })}>
    <MemoryRouter initialEntries={[`/app/deletions/${id}`]}><Routes><Route path="/app/deletions/:jobId" element={<SourceDeletionPage />} /></Routes></MemoryRouter>
  </QueryClientProvider>)
}
beforeEach(() => { vi.resetAllMocks(); session() })
afterEach(cleanup)

it('keeps a not-yet-registered request unavailable and refreshes that same job', async () => {
  api.getSourceDeletion.mockRejectedValueOnce(new ApiError('not registered', 404)).mockResolvedValue(job)
  open()
  expect(await screen.findByRole('alert')).toHaveTextContent('Progress is not available yet')
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh status' }))
  expect(await screen.findByText('Cleanup in progress')).toBeVisible()
  expect(api.getSourceDeletion).toHaveBeenLastCalledWith(id, expect.any(AbortSignal))
})

it('distinguishes recovery acknowledgement and retained records from completion', async () => {
  api.getSourceDeletion.mockResolvedValue({ ...job, state: 'WAITING_FOR_REPLICA', parts: [{ source: 'media', state: 'PRESERVED', errorCode: null }] })
  open()
  expect(await screen.findByText('Recovery acknowledgement pending')).toBeVisible()
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
  await userEvent.setup().click(screen.getByText('Service results'))
  expect(screen.getByText('Restricted records retained')).toBeVisible()
})

it('does not retain an old account result after account switch', async () => {
  let resolve!: (value: typeof job) => void
  api.getSourceDeletion.mockReturnValueOnce(new Promise(done => { resolve = done })).mockRejectedValue(new ApiError('other account', 404))
  open()
  const signal = api.getSourceDeletion.mock.calls[0][1] as AbortSignal
  await act(async () => session('other'))
  await act(async () => resolve({ ...job, state: 'COMPLETE' }))
  expect(signal.aborted).toBe(true)
  expect(await screen.findByRole('alert')).toHaveTextContent('Progress is not available yet')
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
})

it('hides stale status when a later status read loses authority', async () => {
  api.getSourceDeletion.mockResolvedValueOnce({ ...job, state: 'COMPLETE' }).mockRejectedValue(new ApiError('not available', 404))
  open()
  expect(await screen.findByText('Deletion completed')).toBeVisible()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Refresh status' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Progress is not available yet')
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
})
