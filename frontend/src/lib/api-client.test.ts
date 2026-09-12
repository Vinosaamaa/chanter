import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch, apiFetchResponse, configureApiAuth } from './api-client'

describe('authenticated HTTP requests', () => {
  let token: string | null
  let generation: number
  const refresh = vi.fn()
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    token = 'old-token'
    generation = 1
    vi.resetAllMocks()
    vi.stubGlobal('fetch', fetchMock)
    configureApiAuth({ getAccessToken: () => token, getSessionGeneration: () => generation, refreshSession: refresh })
  })

  it('uses cookies and CSRF protection for browser auth mutations without serializing a refresh token', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await apiFetch('/api/v1/auth/refresh', { method: 'POST', skipAuthRefresh: true })
    const [, init] = fetchMock.mock.calls[0]
    expect(init?.credentials).toBe('include')
    expect(new Headers(init?.headers).get('X-Chanter-CSRF')).toBe('1')
    expect(init?.body).toBeUndefined()
  })

  it('refreshes once and retries an expired token with the new access token', async () => {
    const cancel = vi.fn()
    fetchMock.mockResolvedValueOnce(new Response(new ReadableStream({ cancel }), { status: 401 }))
      .mockResolvedValueOnce(Response.json({ title: 'My course' }))
    refresh.mockImplementation(async () => { token = 'new-token'; return true })
    await expect(apiFetch('/api/v1/courses')).resolves.toEqual({ title: 'My course' })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(cancel).toHaveBeenCalledTimes(1)
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe('Bearer new-token')
  })

  it('discards a late account response and never retries it as the next account', async () => {
    let resolve: (response: Response) => void = () => {}
    fetchMock.mockReturnValue(new Promise<Response>((done) => { resolve = done }))
    const result = apiFetch('/api/v1/private-owner-data')
    token = 'learner-token'
    generation += 1
    resolve(new Response(null, { status: 401 }))
    await expect(result).rejects.toMatchObject({ name: 'AbortError' })
    expect(refresh).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('discards a successful response body that finishes after the account changes', async () => {
    let resolveBody: (body: string) => void = () => {}
    const response = Response.json({})
    vi.spyOn(response, 'text').mockReturnValue(new Promise((done) => { resolveBody = done }))
    fetchMock.mockResolvedValue(response)
    const result = apiFetch('/api/v1/private-owner-data')
    await vi.waitFor(() => expect(response.text).toHaveBeenCalled())
    generation += 1
    resolveBody('{"private":"owner"}')
    await expect(result).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('rejects a buffered stream from the previous account before the first read', async () => {
    const cancel = vi.fn()
    fetchMock.mockResolvedValue(new Response(new ReadableStream({
      start(controller) { controller.enqueue(new TextEncoder().encode('private owner answer')) },
      cancel,
    })))
    const response = await apiFetchResponse('/api/v1/answer/stream')
    generation += 1
    await expect(response.body!.getReader().read()).rejects.toMatchObject({ name: 'AbortError' })
    expect(cancel).toHaveBeenCalledTimes(1)
  })

  it('discards a stream chunk that arrives while an account change interrupts a pending read', async () => {
    let upstream: ReadableStreamDefaultController<Uint8Array>
    const cancel = vi.fn()
    fetchMock.mockResolvedValue(new Response(new ReadableStream<Uint8Array>({
      start(controller) { upstream = controller },
      cancel,
    }), { headers: { 'Content-Type': 'text/event-stream' } }))
    const response = await apiFetchResponse('/api/v1/answer/stream')
    const reader = response.body!.getReader()
    upstream!.enqueue(new TextEncoder().encode('first account chunk'))
    expect(new TextDecoder().decode((await reader.read()).value)).toBe('first account chunk')
    const pending = reader.read()
    await Promise.resolve()
    generation += 1
    upstream!.enqueue(new TextEncoder().encode('late private chunk'))
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(cancel).toHaveBeenCalledTimes(1)
    expect(response.headers.get('Content-Type')).toBe('text/event-stream')
  })

  it('propagates consumer cancellation and closes a normal streaming response', async () => {
    const cancel = vi.fn()
    fetchMock.mockResolvedValueOnce(new Response(new ReadableStream({ cancel })))
      .mockResolvedValueOnce(new Response('complete answer'))
    const cancelled = await apiFetchResponse('/api/v1/answer/stream')
    await cancelled.body!.cancel('left the question')
    expect(cancel).toHaveBeenCalledWith('left the question')
    const complete = await apiFetchResponse('/api/v1/answer/stream')
    await expect(complete.text()).resolves.toBe('complete answer')
  })
})
