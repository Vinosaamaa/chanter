type SocketListener = ((event?: { data?: string; code?: number }) => void) | null

/** Shared WebSocket stand-in for reconnect-auth unit tests. */
export class MockWebSocket {
  static OPEN = 1
  static instances: MockWebSocket[] = []

  readyState = 0
  url: string
  protocols: string | string[]
  onopen: SocketListener = null
  onmessage: SocketListener = null
  onerror: SocketListener = null
  onclose: SocketListener = null
  send = () => undefined
  close = () => {
    this.readyState = 3
    this.onclose?.({ code: 1000 })
  }

  constructor(url: string, protocols?: string | string[]) {
    this.url = url
    this.protocols = protocols ?? []
    MockWebSocket.instances.push(this)
  }

  open(): void {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  static reset(): void {
    MockWebSocket.instances = []
  }
}
