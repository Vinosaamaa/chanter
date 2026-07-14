import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchChannelMessages } from '../channel-messages-api'

import { useChannelConversation } from './use-channel-conversation'

const realtime = vi.hoisted(() => ({
  options: [] as Array<{
    onStatusChange: (status: 'connecting' | 'connected' | 'reconnecting' | 'disconnected') => void
  }>,
}))

vi.mock('../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { accessToken: string; user: { id: string } }) => unknown) =>
    selector({ accessToken: 'token', user: { id: 'user-1' } }),
}))

vi.mock('../channel-messages-api', () => ({
  fetchChannelMessages: vi.fn(),
}))

vi.mock('../../realtime/realtime-client', () => ({
  RealtimeClient: class {
    constructor(options: (typeof realtime.options)[number]) {
      realtime.options.push(options)
    }
    connect() {}
    disconnect() {}
    send() {}
  },
}))

const mockedFetchMessages = vi.mocked(fetchChannelMessages)

describe('useChannelConversation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realtime.options.length = 0
  })

  it('clears stale messages immediately when the selected channel changes', async () => {
    let resolveSecond: ((value: { messages: never[] }) => void) | undefined
    mockedFetchMessages
      .mockResolvedValueOnce({
        messages: [{
          id: 'message-1',
          channelId: 'channel-1',
          senderUserId: 'user-2',
          body: 'First channel message',
          createdAt: '2026-07-14T10:00:00Z',
        }],
      })
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveSecond = resolve
      }))

    const { result, rerender } = renderHook(
      ({ channelId }) => useChannelConversation('course', channelId),
      { initialProps: { channelId: 'channel-1' } },
    )
    await waitFor(() => expect(result.current.messages).toHaveLength(1))

    rerender({ channelId: 'channel-2' })

    expect(result.current.messages).toEqual([])
    expect(result.current.isLoadingHistory).toBe(true)

    await act(async () => {
      resolveSecond?.({ messages: [] })
    })
  })

  it('reconciles after initial history when realtime connects first', async () => {
    let resolveHistory: ((value: {
      messages: Array<{
        id: string
        channelId: string
        senderUserId: string
        body: string
        createdAt: string
      }>
    }) => void) | undefined
    mockedFetchMessages
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveHistory = resolve
      }))
      .mockResolvedValueOnce({ messages: [] })

    renderHook(() => useChannelConversation('course', 'channel-1'))
    act(() => realtime.options[0].onStatusChange('connected'))

    await act(async () => {
      resolveHistory?.({
        messages: [{
          id: 'history-message',
          channelId: 'channel-1',
          senderUserId: 'user-2',
          body: 'History snapshot',
          createdAt: '2026-07-14T10:00:00Z',
        }],
      })
    })

    await waitFor(() => expect(mockedFetchMessages).toHaveBeenCalledTimes(2))
    expect(mockedFetchMessages).toHaveBeenLastCalledWith(
      'course',
      'channel-1',
      '2026-07-14T10:00:00Z',
      'history-message',
    )
  })
})
