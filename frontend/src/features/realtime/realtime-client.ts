import { getApiBase } from '../../lib/api-base'

import {
  type ChannelMessage,
  type ChannelScope,
  type RealtimeServerMessage,
  toRealtimeChannelScope,
} from '../shell/channel-message-types'

type RealtimeClientOptions = {
  accessToken: string
  channelId: string
  channelScope: ChannelScope
  onMessage: (message: ChannelMessage) => void
  onStatusChange: (status: RealtimeConnectionStatus) => void
  onError: (message: string) => void
}

export type RealtimeConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'disconnected'

type OutboundFrame =
  | {
      type: 'subscribe'
      channelId: string
      channelScope: 'STUDY_SERVER' | 'COURSE'
    }
  | {
      type: 'unsubscribe'
    }
  | {
      type: 'send'
      channelId: string
      channelScope: 'STUDY_SERVER' | 'COURSE'
      body: string
    }

const RECONNECT_BASE_MS = 500
const RECONNECT_MAX_MS = 5_000

export class RealtimeClient {
  private socket: WebSocket | null = null
  private reconnectAttempts = 0
  private stopped = false
  private lastEventAt: string | null = null
  private readonly options: RealtimeClientOptions

  constructor(options: RealtimeClientOptions) {
    this.options = options
  }

  connect(): void {
    this.stopped = false
    this.openSocket()
  }

  disconnect(): void {
    this.stopped = true
    const socket = this.socket
    this.socket = null
    if (socket) {
      socket.onopen = null
      socket.onmessage = null
      socket.onerror = null
      socket.onclose = null
      socket.close()
    }
    this.options.onStatusChange('disconnected')
  }

  send(body: string): void {
    this.sendFrame({
      type: 'send',
      channelId: this.options.channelId,
      channelScope: toRealtimeChannelScope(this.options.channelScope),
      body,
    })
  }

  getLastEventAt(): string | null {
    return this.lastEventAt
  }

  private openSocket(): void {
    const status: RealtimeConnectionStatus =
      this.reconnectAttempts === 0 ? 'connecting' : 'reconnecting'
    this.options.onStatusChange(status)

    const apiBase = getApiBase()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = apiBase
      ? new URL(apiBase).host
      : window.location.host
    const path = '/api/v1/realtime/ws'
    const url = `${protocol}//${host}${path}?access_token=${encodeURIComponent(this.options.accessToken)}`

    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempts = 0
      this.options.onStatusChange('connected')
      this.sendFrame({
        type: 'subscribe',
        channelId: this.options.channelId,
        channelScope: toRealtimeChannelScope(this.options.channelScope),
      })
    }

    socket.onmessage = (event) => {
      try {
        const frame = JSON.parse(String(event.data)) as RealtimeServerMessage
        if (frame.type === 'message') {
          this.lastEventAt = frame.payload.createdAt
          this.options.onMessage(frame.payload)
          return
        }
        if (frame.type === 'error') {
          this.options.onError(frame.message)
        }
      } catch {
        this.options.onError('Received an invalid realtime event')
      }
    }

    socket.onclose = () => {
      this.socket = null
      if (this.stopped) {
        this.options.onStatusChange('disconnected')
        return
      }
      this.scheduleReconnect()
    }

    socket.onerror = () => {
      if (this.stopped) {
        return
      }
      this.options.onError('Realtime connection failed')
    }
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts += 1
    const delay = Math.min(RECONNECT_BASE_MS * this.reconnectAttempts, RECONNECT_MAX_MS)
    this.options.onStatusChange('reconnecting')
    window.setTimeout(() => {
      if (!this.stopped) {
        this.openSocket()
      }
    }, delay)
  }

  private sendFrame(frame: OutboundFrame): void {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      throw new Error('Realtime connection is not ready')
    }
    this.socket.send(JSON.stringify(frame))
  }
}
