import { describe, expect, it, vi } from 'vitest'
import { apiFetchResponse } from '../../lib/api-client'
import { streamAssistantAnswer } from './questions-api'

vi.mock('../../lib/api-client', async (original) => ({
  ...await original<typeof import('../../lib/api-client')>(), apiFetchResponse: vi.fn(),
}))

function responseWith(events: string) {
  const stream = new ReadableStream<Uint8Array>({ start(controller) {
    controller.enqueue(new TextEncoder().encode(events))
    controller.close()
  } })
  vi.mocked(apiFetchResponse).mockResolvedValueOnce(new Response(stream))
  return stream
}

describe('assistant stream completion contract', () => {
  it('rejects EOF after draft text without treating it as a completed answer', async () => {
    const stream = responseWith('event: token\ndata: Partial quotation\n\n')
    const onToken = vi.fn()
    const onComplete = vi.fn()
    await expect(streamAssistantAnswer('channel', 'question', { onToken, onComplete }))
      .rejects.toMatchObject({ code: 'STREAM_INTERRUPTED', status: 502 })
    expect(onToken).toHaveBeenCalledWith('Partial quotation')
    expect(onComplete).not.toHaveBeenCalled()
    expect(stream.locked).toBe(false)
  })

  it('handles retrieval status and rejects structured errors while preserving their recovery code', async () => {
    const stream = responseWith('event: status\ndata: retrieving\n\nevent: error\ndata: {"code":"GENERATION_ALREADY_ATTEMPTED","status":409,"message":"Use approved sources or ask an instructor."}\n\n')
    const onStatus = vi.fn()
    const onComplete = vi.fn()
    await expect(streamAssistantAnswer('channel', 'question', { onStatus, onToken: vi.fn(), onComplete }))
      .rejects.toMatchObject({ code: 'GENERATION_ALREADY_ATTEMPTED', status: 409, message: 'Use approved sources or ask an instructor.' })
    expect(onStatus).toHaveBeenCalledWith('retrieving')
    expect(onComplete).not.toHaveBeenCalled()
    expect(stream.locked).toBe(false)
  })

  it('does not accept an unterminated completion event at EOF', async () => {
    responseWith('event: complete\ndata: {}\n')
    const onComplete = vi.fn()
    await expect(streamAssistantAnswer('channel', 'question', { onToken: vi.fn(), onComplete }))
      .rejects.toMatchObject({ code: 'STREAM_INTERRUPTED' })
    expect(onComplete).not.toHaveBeenCalled()
  })

  it('sends explicit model and mode as encoded query parameters', async () => {
    responseWith('event: complete\ndata: {}\n\n')
    await streamAssistantAnswer('channel', 'question', { onToken: vi.fn(), onComplete: vi.fn() }, {
      modelId: 'model/one', answerMode: 'quoted-evidence',
    })
    expect(apiFetchResponse).toHaveBeenLastCalledWith(
      '/api/v1/course-channels/channel/support-questions/question/assistant-answer/stream?modelId=model%2Fone&answerMode=quoted-evidence',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
