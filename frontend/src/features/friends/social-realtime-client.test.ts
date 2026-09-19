import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { MockWebSocket } from '../../test/mock-websocket'
import { SocialRealtimeClient } from './social-realtime-client'

describe('SocialRealtimeClient reconnect refresh', () => {
  beforeEach(() => {
    MockWebSocket.reset()
    vi.stubGlobal('WebSocket', MockWebSocket)
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('refreshes and uses the latest access token on reconnect', async () => {
    let token = 'stale-token'
    const refreshSession = vi.fn(async () => {
      token = 'fresh-token'
      return true
    })

    const client = new SocialRealtimeClient({
      getAccessToken: () => token,
      refreshSession,
      onDirectMessage: vi.fn(),
      onPresenceChange: vi.fn(),
      onCallEvent: vi.fn(),
      onStatusChange: vi.fn(),
      onError: vi.fn(),
    })

    client.connect()
    expect(MockWebSocket.instances[0]?.protocols).toEqual(['chanter-jwt', 'stale-token'])
    MockWebSocket.instances[0]?.open()
    MockWebSocket.instances[0]?.onclose?.({ code: 1006 })

    await vi.advanceTimersByTimeAsync(1_000)
    await Promise.resolve()
    await Promise.resolve()

    expect(refreshSession).toHaveBeenCalledTimes(1)
    expect(MockWebSocket.instances).toHaveLength(2)
    expect(MockWebSocket.instances[1]?.protocols).toEqual(['chanter-jwt', 'fresh-token'])
  })

  it('clears old online peers when a current presence snapshot excludes them', () => {
    const onPresenceChange=vi.fn()
    const client=new SocialRealtimeClient({getAccessToken:()=> 'token',refreshSession:async()=>true,
      onDirectMessage:vi.fn(),onPresenceChange,onCallEvent:vi.fn(),onStatusChange:vi.fn(),onError:vi.fn()})
    client.connect()
    const socket=MockWebSocket.instances[0]!
    socket.onmessage?.({data:JSON.stringify({type:'presence_changed',userId:'peer',status:'online'})})
    socket.onmessage?.({data:JSON.stringify({type:'presence_snapshot',onlineUserIds:[]})})
    expect(onPresenceChange).toHaveBeenLastCalledWith('peer','offline')
    client.disconnect()
  })
})
