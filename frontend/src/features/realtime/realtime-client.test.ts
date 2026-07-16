import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { MockWebSocket } from '../../test/mock-websocket'
import { RealtimeClient } from './realtime-client'

describe('RealtimeClient reconnect refresh', () => {
  beforeEach(() => {
    MockWebSocket.reset()
    vi.stubGlobal('WebSocket', MockWebSocket)
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('reads a fresh access token and refreshes before reconnect openSocket', async () => {
    let token = 'stale-token'
    const refreshSession = vi.fn(async () => {
      token = 'fresh-token'
      return true
    })

    const client = new RealtimeClient({
      getAccessToken: () => token,
      refreshSession,
      channelId: 'channel-1',
      channelScope: 'course',
      onMessage: vi.fn(),
      onStatusChange: vi.fn(),
      onError: vi.fn(),
    })

    client.connect()
    expect(MockWebSocket.instances).toHaveLength(1)
    expect(MockWebSocket.instances[0]?.protocols).toEqual(['chanter-jwt', 'stale-token'])
    MockWebSocket.instances[0]?.open()

    MockWebSocket.instances[0]?.onclose?.({ code: 1006 })

    await vi.advanceTimersByTimeAsync(500)
    await Promise.resolve()
    await Promise.resolve()

    expect(refreshSession).toHaveBeenCalledTimes(1)
    expect(MockWebSocket.instances).toHaveLength(2)
    expect(MockWebSocket.instances[1]?.protocols).toEqual(['chanter-jwt', 'fresh-token'])
  })

  it('does not refresh on the initial connect', () => {
    const refreshSession = vi.fn(async () => true)
    const client = new RealtimeClient({
      getAccessToken: () => 'initial-token',
      refreshSession,
      channelId: 'channel-1',
      channelScope: 'course',
      onMessage: vi.fn(),
      onStatusChange: vi.fn(),
      onError: vi.fn(),
    })

    client.connect()
    expect(refreshSession).not.toHaveBeenCalled()
    expect(MockWebSocket.instances[0]?.protocols).toEqual(['chanter-jwt', 'initial-token'])
  })
})
