import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { checkNativeConnection, parseNativePairing, streamNativeAnswer } from './native-companion-api'

vi.mock('../../lib/api-client', () => ({ apiFetch: vi.fn() }))
const installationId = 'c17a50e8-6a31-41d7-9fe4-ec34e85f9535'
const pairing = () => ({ installationId, handle: 'x'.repeat(43), expiresAt: Date.now() + 200_000 })
const status = { account: { state: 'subscription' }, models: [{ model: 'allowed' }, { model: 'provider-only' }],
  limits: { primary: { usedPercent: 20 }, secondary: { usedPercent: 30 }, spendControlReached: false } }
const config = () => ({ available: true, origin: window.location.origin, models: ['allowed', 'deployment-only'] })

describe('native browser boundary', () => {
  beforeEach(() => { vi.resetAllMocks(); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => vi.unstubAllGlobals())
  it('sends only the pairing handle and signed capability to fixed loopback and intersects models', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(config()).mockResolvedValueOnce({ ticket: 'signed-status' })
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify(status)))
    expect(await checkNativeConnection(pairing())).toEqual(['allowed'])
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('http://127.0.0.1:43160/status')
    expect(init).toMatchObject({ credentials: 'omit', redirect: 'error', body: JSON.stringify({ ticket: 'signed-status' }) })
    expect(Object.keys(init!.headers!)).toEqual(['Content-Type', 'X-Chanter-Pairing'])
    expect(init!.headers).not.toHaveProperty('Authorization')
  })
  it('does not reserve a turn when subscription limits are unknown or the model is no longer allowed', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(config()).mockResolvedValueOnce({ ticket: 'signed-status' })
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ ...status, limits: null })))
    await expect(streamNativeAnswer('channel', 'question', pairing(), 'allowed', { onComplete: vi.fn(), onToken: vi.fn() })).rejects.toThrow('unknown')
    expect(apiFetch).toHaveBeenCalledTimes(2)
  })
  it('publishes only the authoritative saved answer and never displays raw native deltas', async () => {
    const saved = { id: 'server-saved-answer' }, onToken = vi.fn(), onComplete = vi.fn()
    vi.mocked(apiFetch).mockResolvedValueOnce(config()).mockResolvedValueOnce({ ticket: 'status' })
      .mockResolvedValueOnce({ ticket: 'study', prompt: 'authorized evidence', requestId: 'request' }).mockResolvedValueOnce(saved)
    const result = { text: '{"sourceId":"S1","quote":"evidence"}\n', usage: { inputTokens: 0, outputTokens: 0 } }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify(status)))
      .mockResolvedValueOnce(new Response(`event: delta\ndata: {"text":"unverified"}\n\nevent: completed\ndata: ${JSON.stringify(result)}\n\n`))
    await streamNativeAnswer('channel', 'question', pairing(), 'allowed', { onComplete, onToken })
    expect(onToken).not.toHaveBeenCalled()
    expect(onComplete).toHaveBeenCalledExactlyOnceWith(saved)
    expect(apiFetch).toHaveBeenLastCalledWith('/api/v1/course-channels/channel/support-questions/question/native-results/request',
      expect.objectContaining({ body: JSON.stringify({ installationId, ...result }) }))
  })
  it('cancels local traffic on logout and cannot submit a result under the next account', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(config()).mockResolvedValueOnce({ ticket: 'status' })
    vi.mocked(fetch).mockImplementationOnce(async (_url, init) => {
      useAuthStore.getState().clearSession()
      expect(init!.signal!.aborted).toBe(true)
      throw new DOMException('aborted', 'AbortError')
    })
    await expect(checkNativeConnection(pairing())).rejects.toMatchObject({ name: 'AbortError' })
    expect(apiFetch).toHaveBeenCalledTimes(2)
  })
  it('rejects expired pairing and duplicate completion without accepting or retrying output', async () => {
    expect(() => parseNativePairing(installationId, JSON.stringify({ handle: 'x'.repeat(43), expiresAt: 1 }))).toThrow('expired')
    vi.mocked(apiFetch).mockResolvedValueOnce(config()).mockResolvedValueOnce({ ticket: 'status' })
      .mockResolvedValueOnce({ ticket: 'study', prompt: 'evidence', requestId: 'request' })
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify(status))).mockResolvedValueOnce(new Response(
      'event: completed\ndata: {"text":"a"}\n\nevent: completed\ndata: {"text":"b"}\n\n'))
    const onComplete = vi.fn()
    await expect(streamNativeAnswer('channel', 'question', pairing(), 'allowed', { onComplete, onToken: vi.fn() })).rejects.toThrow('invalid')
    expect(onComplete).not.toHaveBeenCalled(); expect(apiFetch).toHaveBeenCalledTimes(3)
  })
})
