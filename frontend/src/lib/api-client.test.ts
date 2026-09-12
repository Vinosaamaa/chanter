import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch, configureApiAuth } from './api-client'

describe('authenticated HTTP requests', () => {
  let token: string | null
  let generation: number
  const refresh = vi.fn()
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    token = 'old-token'
    generation = 1
    vi.clearAllMocks()
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
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ title: 'My course' }))
    refresh.mockImplementation(async () => { token = 'new-token'; return true })
    await expect(apiFetch('/api/v1/courses')).resolves.toEqual({ title: 'My course' })
    expect(refresh).toHaveBeenCalledTimes(1)
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
})
