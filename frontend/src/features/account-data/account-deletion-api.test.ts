import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { configureApiAuth } from '../../lib/api-client'
import { cancelDeletion, confirmDeletion, getDeletionReceipt, prepareDeletion } from './account-deletion-api'
const id = 'b633c892-6762-40ec-a945-b042957a052b'
const fetchMock = vi.fn()
const refresh = vi.fn()
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset(); refresh.mockReset()
  configureApiAuth({ getAccessToken: () => 'ordinary-token', getSessionGeneration: () => 1, refreshSession: refresh })
})
afterEach(() => vi.unstubAllGlobals())

it('reads a receipt with only cookie authority and never refreshes ordinary credentials', async () => {
  fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))
  await getDeletionReceipt(id)
  const [url, options] = fetchMock.mock.calls[0]
  expect(url).toBe(`${window.location.origin}/api/v1/auth/account/deletions/${id}/receipt`)
  expect(options.credentials).toBe('include')
  expect(options.cache).toBe('no-store')
  expect(new Headers(options.headers).has('Authorization')).toBe(false)
  fetchMock.mockResolvedValue(new Response('', { status: 401 }))
  await expect(getDeletionReceipt(id)).rejects.toMatchObject({ status: 401 })
  expect(refresh).not.toHaveBeenCalled()
})

it('retains authenticated CSRF protection for prepare, confirm and cancel', async () => {
  fetchMock.mockImplementation(() => Promise.resolve(new Response('{}', { status: 202 })))
  await prepareDeletion(id); await confirmDeletion(id); await cancelDeletion(id)
  for (const [, options] of fetchMock.mock.calls) {
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer ordinary-token')
    expect(new Headers(options.headers).get('X-Chanter-CSRF')).toBe('1')
  }
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ requestId: id })
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ confirmation: 'DELETE MY ACCOUNT' })
})

it('rejects receipt path injection before network access', async () => {
  await expect(getDeletionReceipt(`${id}/../other`)).rejects.toThrow('Invalid deletion')
  expect(fetchMock).not.toHaveBeenCalled()
})
