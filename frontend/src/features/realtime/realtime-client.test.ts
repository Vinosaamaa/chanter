import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { RealtimeClient } from './realtime-client'

type SocketListener = ((event?: { data?: string; code?: number }) => void) | null

class MockWebSocket {
  static OPEN = 1
  static instances: MockWebSocket[] = []

  readyState = 0
  protocols: string | string[]
  onopen: SocketListener = null
  onmessage: SocketListener = null
  onerror: SocketListener = null
  onclose: SocketListener = null
  send = vi.fn()
  close = vi.fn(() => {
    this.readyState = 3
    this.onclose?.({ code: 1000 })
  })

  constructor(
    public url: string,
    protocols?: string | string[],
  ) {
    this.protocols = protocols ?? []
    MockWebSocket.instances.push(this)
  }

  open(): void {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }
}

describe('RealtimeClient reconnect refresh', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
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
    const onStatusChange = vi.fn()

    const client = new RealtimeClient({
      getAccessToken: () => token,
      refreshSession,
      channelId: 'channel-1',
      channelScope: 'course',
      onMessage: vi.fn(),
      onStatusChange,
      onError: vi.fn(),
    })

    client.connect()
    expect(MockWebSocket.instances).toHaveLength(1)
    expect(MockWebSocket.instances[0]?.protocols).toEqual(['chanter-jwt', 'stale-token'])
    MockWebSocket.instances[0]?.open()

    // Simulate server closing the idle socket (expired auth path).
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
