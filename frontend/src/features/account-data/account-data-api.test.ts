import { beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ fetch: vi.fn(), base: vi.fn(() => '') }))
vi.mock('../../lib/api-client', () => ({ apiFetch: api.fetch }))
vi.mock('../../lib/api-base', () => ({ getApiBase: api.base }))
import { authorizeExportDownload, cancelExport, createExport, listExports } from './account-data-api'

const id = 'b633c892-6762-40ec-a945-b042957a052b'
describe('account export API', () => {
  beforeEach(() => { vi.clearAllMocks(); api.base.mockReturnValue(''); api.fetch.mockResolvedValue(undefined) })
  it('uses authenticated issuance then returns only a fixed same-origin URL', async () => {
    const signal = new AbortController().signal
    expect(await authorizeExportDownload(id, signal)).toBe(`${window.location.origin}/api/v1/auth/account/exports/${id}/download`)
    expect(api.fetch).toHaveBeenCalledExactlyOnceWith(`/api/v1/auth/account/exports/${id}/download-authorization`, { method: 'POST', signal })
  })
  it('rejects path injection and cross-origin download before issuing a grant', async () => {
    await expect(authorizeExportDownload(`${id}?token=unsafe`)).rejects.toThrow('Invalid export')
    api.base.mockReturnValue('https://other.example.test')
    await expect(authorizeExportDownload(id)).rejects.toThrow('same-origin API')
    expect(api.fetch).not.toHaveBeenCalled()
  })
  it('uses actual list shape and sends stable creation and cancellation scope', async () => {
    api.fetch.mockResolvedValueOnce([])
    expect(await listExports()).toEqual([])
    await createExport(id)
    expect(api.fetch).toHaveBeenLastCalledWith('/api/v1/auth/account/exports', { method: 'POST', body: JSON.stringify({ requestId: id }), signal: undefined })
    await cancelExport(id)
    expect(api.fetch).toHaveBeenLastCalledWith(`/api/v1/auth/account/exports/${id}`, { method: 'DELETE', signal: undefined })
  })
})
