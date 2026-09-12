import { describe, expect, it, vi } from 'vitest'
import { apiFetchResponse } from '../../lib/api-client'
import { streamAssistantAnswer } from './questions-api'

vi.mock('../../lib/api-client', () => ({ apiFetchResponse: vi.fn(), apiFetch: vi.fn(), ApiError: class extends Error {} }))

describe('assistant stream ownership', () => {
  it.each([
    ['invalid final JSON', 'event: complete\ndata: invalid\n\n', false],
    ['token callback failure', 'event: token\ndata: a token\n\n', true],
    ['completion callback failure', 'event: complete\ndata: {}\n\n', true],
  ] as const)('cancels and releases the response after %s', async (_name, event, callbackThrows) => {
    const cancel = vi.fn()
    const stream = new ReadableStream({
      start(controller) { controller.enqueue(new TextEncoder().encode(event)) },
      cancel,
    })
    vi.mocked(apiFetchResponse).mockResolvedValueOnce(new Response(stream))
    const callback = () => { if (callbackThrows) throw new Error('Consumer failed') }
    await expect(streamAssistantAnswer('channel', 'question', { onToken: callback, onComplete: callback })).rejects.toThrow()
    expect(cancel).toHaveBeenCalledOnce()
    expect(stream.locked).toBe(false)
  })

  it('releases a completed stream without cancelling it', async () => {
    const cancel = vi.fn()
    const stream = new ReadableStream({
      start(controller) { controller.enqueue(new TextEncoder().encode('event: complete\ndata: {}\n\n')); controller.close() },
      cancel,
    })
    vi.mocked(apiFetchResponse).mockResolvedValueOnce(new Response(stream))
    const complete = vi.fn()
    await streamAssistantAnswer('channel', 'question', { onToken: vi.fn(), onComplete: complete })
    expect(complete).toHaveBeenCalledOnce()
    expect(cancel).not.toHaveBeenCalled()
    expect(stream.locked).toBe(false)
  })
})
