import { afterEach, expect, it, vi } from 'vitest'
import { bootstrapDemoPersonas } from './demo-auth'

afterEach(() => vi.unstubAllGlobals())

it('bootstraps demo bearer tokens without reading or replacing browser session cookies', async () => {
  const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (_url, init) => {
    const { email } = JSON.parse(init!.body as string)
    return Response.json({ accessToken: 'demo-access', user: { id: email, email, displayName: 'Demo' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  const personas = await bootstrapDemoPersonas()
  expect(Object.keys(personas)).toHaveLength(4)
  for (const [, init] of fetchMock.mock.calls) expect(init?.credentials).toBe('omit')
})

it('never accepts a cookie when registering a missing demo persona', async () => {
  const fetchMock = vi.fn<typeof fetch>()
    .mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ message: 'Check your inbox' }, { status: 202 }))
  vi.stubGlobal('fetch', fetchMock)
  await expect(bootstrapDemoPersonas()).rejects.toThrow('Verify the local demo accounts')
  expect(fetchMock).toHaveBeenCalledTimes(2)
  for (const [, init] of fetchMock.mock.calls) expect(init?.credentials).toBe('omit')
})
