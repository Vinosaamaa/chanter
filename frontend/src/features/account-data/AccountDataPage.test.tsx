import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { AccountDataPage } from './AccountDataPage'
import type { ExportJob } from './account-data-api'

const api = vi.hoisted(() => ({ listExports: vi.fn(), createExport: vi.fn(), cancelExport: vi.fn(), authorizeExportDownload: vi.fn() }))
vi.mock('./account-data-api', () => api)
const job: ExportJob = {
  schemaVersion: 1, id: 'b633c892-6762-40ec-a945-b042957a052b', accountId: 'owner',
  requestedAt: '2026-09-22T18:00:00Z', expiresAt: '2026-09-23T18:00:00Z', state: 'BUILDING', cleanupPending: false,
  parts: ['auth', 'community', 'message', 'media', 'agent', 'notification', 'search'].map(source => ({ source, state: source === 'auth' ? 'READY' : 'PENDING', errorCode: null })),
}
function session(id = 'owner') { useAuthStore.getState().setSession({ accessToken: `${id}-token`, expiresInSeconds: 900, user: { id, email: `${id}@example.test`, displayName: id } }) }
function open() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })}><MemoryRouter><AccountDataPage /></MemoryRouter></QueryClientProvider>)
}
describe('account export controls', () => {
  beforeEach(() => { vi.resetAllMocks(); session(); api.listExports.mockResolvedValue([]); vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {}) })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('keeps uncertain creation idempotent and shows actual source progress without polling', async () => {
    api.createExport.mockRejectedValueOnce(new Error('connection lost')).mockResolvedValueOnce(job)
    const user = userEvent.setup(); open()
    await user.click(await screen.findByRole('button', { name: 'Request export' }))
    await screen.findByRole('alert')
    await user.click(screen.getByRole('button', { name: 'Request export' }))
    expect(await screen.findByText(/1 of 7 sources prepared/)).toBeVisible()
    expect(api.createExport.mock.calls[0][0]).toBe(api.createExport.mock.calls[1][0])
    expect(screen.getByRole('button', { name: 'Request export' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Download ZIP' })).not.toBeInTheDocument()
    expect(api.listExports).toHaveBeenCalledTimes(1)
  })

  it('starts browser navigation only after authorization and never claims the file was saved', async () => {
    api.listExports.mockResolvedValue([{ ...job, state: 'READY' }])
    const url = `/api/v1/auth/account/exports/${job.id}/download`
    api.authorizeExportDownload.mockResolvedValue(url)
    const user = userEvent.setup(); open()
    await user.click(await screen.findByRole('button', { name: 'Download ZIP' }))
    expect(api.authorizeExportDownload).toHaveBeenCalledWith(job.id, expect.any(AbortSignal))
    expect(screen.getByTitle('Account export download')).toHaveAttribute('href', url)
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1)
    expect(await screen.findByText(/Download requested. Check your browser/)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Download ZIP' }))
    expect(api.authorizeExportDownload).toHaveBeenCalledTimes(2)
    expect(screen.queryByText(/file saved|download complete/i)).not.toBeInTheDocument()
  })

  it('prevents a late authorization response from navigating after an account switch', async () => {
    api.listExports.mockResolvedValueOnce([{ ...job, state: 'READY' }]).mockResolvedValue([])
    let release!: (url: string) => void
    api.authorizeExportDownload.mockReturnValue(new Promise<string>(resolve => { release = resolve }))
    const user = userEvent.setup(); open()
    await user.click(await screen.findByRole('button', { name: 'Download ZIP' }))
    const signal = api.authorizeExportDownload.mock.calls[0][1] as AbortSignal
    await act(async () => { session('other') })
    await act(async () => release(`/api/v1/auth/account/exports/${job.id}/download`))
    expect(signal.aborted).toBe(true)
    expect(screen.getByTitle('Account export download')).not.toHaveAttribute('href')
    expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled()
    expect(screen.queryByText(/Download requested/)).not.toBeInTheDocument()
  })

  it('explains recent authentication and keeps a failed source visibly incomplete', async () => {
    api.createExport.mockRejectedValue(new ApiError('RECENT_LOGIN_REQUIRED', 428))
    const user = userEvent.setup(); open()
    await user.click(await screen.findByRole('button', { name: 'Request export' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('A refreshed session does not count as a new login')
    expect(screen.getByRole('button', { name: 'Sign out to sign in again' })).toBeVisible()
    api.listExports.mockResolvedValue([{ ...job, parts: job.parts.map(part => part.source === 'media' ? { ...part, errorCode: 'DELIVERY_FAILED' } : part) }])
    await user.click(screen.getByRole('button', { name: 'Refresh status' }))
    expect(await screen.findByText('Preparation needs attention')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Download ZIP' })).not.toBeInTheDocument()
  })

  it('reports cancellation cleanup separately and offers explicit retry after a list failure', async () => {
    api.listExports.mockRejectedValueOnce(new Error('offline')).mockResolvedValue([job])
    api.cancelExport.mockResolvedValue({ ...job, state: 'CANCELLED', cleanupPending: true })
    const user = userEvent.setup(); open()
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load export requests')
    expect(screen.getByRole('button', { name: 'Request export' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Refresh status' }))
    await user.click(await screen.findByRole('button', { name: 'Cancel export' }))
    expect(await screen.findByText('Cancelled', { exact: true })).toBeVisible()
    expect(screen.getByText(/Some sources have not yet confirmed removal/)).toBeVisible()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Request export' })).toBeEnabled())
  })
})
