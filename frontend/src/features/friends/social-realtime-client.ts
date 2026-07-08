import { getApiBase } from '../../lib/api-base'

import type { DirectMessage, FriendPresenceStatus, SocialRealtimeMessage } from './types'

type SocialRealtimeClientOptions = {
  accessToken: string
  onDirectMessage: (message: DirectMessage) => void
  onPresenceChange: (userId: string, status: FriendPresenceStatus) => void
  onStatusChange: (status: SocialRealtimeConnectionStatus) => void
  onError: (message: string) => void
}

export type SocialRealtimeConnectionStatus =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'

type OutboundFrame =
  | {
      type: 'send_dm'
      recipientUserId: string
      body: string
    }

const RECONNECT_BASE_MS = 500
const RECONNECT_MAX_MS = 5_000

export class SocialRealtimeClient {
  private socket: WebSocket | null = null
  private reconnectAttempts = 0
  private stopped = false
  private readonly options: SocialRealtimeClientOptions

  constructor(options: SocialRealtimeClientOptions) {
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

  sendDirectMessage(recipientUserId: string, body: string): void {
    this.sendFrame({
      type: 'send_dm',
      recipientUserId,
      body,
    })
  }

  private openSocket(): void {
    const status: SocialRealtimeConnectionStatus =
      this.reconnectAttempts === 0 ? 'connecting' : 'reconnecting'
    this.options.onStatusChange(status)

    const apiBase = getApiBase()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = apiBase ? new URL(apiBase).host : window.location.host
    const path = '/api/v1/realtime/ws'
    const url = `${protocol}//${host}${path}?access_token=${encodeURIComponent(this.options.accessToken)}`

    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempts = 0
      this.options.onStatusChange('connected')
    }

    socket.onmessage = (event) => {
      try {
        const frame = JSON.parse(String(event.data)) as SocialRealtimeMessage
        if (frame.type === 'dm_message') {
          this.options.onDirectMessage(frame.payload)
          return
        }
        if (frame.type === 'presence_changed') {
          this.options.onPresenceChange(frame.userId, frame.status)
          return
        }
        if (frame.type === 'error') {
          this.options.onError(frame.message)
        }
      } catch {
        this.options.onError('Received an invalid social realtime event')
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
      if (!this.stopped) {
        this.options.onError('Social realtime connection failed')
      }
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
      throw new Error('Social realtime connection is not ready')
    }
    this.socket.send(JSON.stringify(frame))
  }
}
