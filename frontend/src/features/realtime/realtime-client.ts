import { getApiBase } from '../../lib/api-base'

import {
  type ChannelMessage,
  type ChannelScope,
  type RealtimeServerMessage,
  toRealtimeChannelScope,
} from '../shell/channel-message-types'

type RealtimeClientOptions = {
  getAccessToken: () => string | null
  refreshSession: () => Promise<boolean>
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
  private reconnectTimerId: number | null = null
  private stopped = false
  private opening = false
  private lastEventAt: string | null = null
  private readonly options: RealtimeClientOptions

  constructor(options: RealtimeClientOptions) {
    this.options = options
  }

  connect(): void {
    this.stopped = false
    this.clearReconnectTimer()
    void this.openSocket()
  }

  disconnect(): void {
    this.stopped = true
    this.clearReconnectTimer()
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

  private async openSocket(): Promise<void> {
    if (this.stopped || this.opening) {
      return
    }
    this.opening = true

    try {
      const status: RealtimeConnectionStatus =
        this.reconnectAttempts === 0 ? 'connecting' : 'reconnecting'
      this.options.onStatusChange(status)

      if (this.reconnectAttempts > 0) {
        await this.options.refreshSession()
        if (this.stopped) {
          return
        }
      }

      const accessToken = this.options.getAccessToken()
      if (!accessToken) {
        this.options.onError('Realtime authentication expired')
        this.scheduleReconnect()
        return
      }

      const apiBase = getApiBase()
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = apiBase
        ? new URL(apiBase).host
        : window.location.host
      const path = '/api/v1/realtime/ws'
      const url = `${protocol}//${host}${path}`

      const socket = new WebSocket(url, ['chanter-jwt', accessToken])
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
    } finally {
      this.opening = false
    }
  }

  private scheduleReconnect(): void {
    this.clearReconnectTimer()
    this.reconnectAttempts += 1
    const delay = Math.min(RECONNECT_BASE_MS * this.reconnectAttempts, RECONNECT_MAX_MS)
    this.options.onStatusChange('reconnecting')
    this.reconnectTimerId = window.setTimeout(() => {
      this.reconnectTimerId = null
      if (!this.stopped) {
        void this.openSocket()
      }
    }, delay)
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimerId !== null) {
      window.clearTimeout(this.reconnectTimerId)
      this.reconnectTimerId = null
    }
  }

  private sendFrame(frame: OutboundFrame): void {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      throw new Error('Realtime connection is not ready')
    }
    this.socket.send(JSON.stringify(frame))
  }
}
