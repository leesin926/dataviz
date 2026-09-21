/**
 * ScreenWebSocket - 大屏 WebSocket 连接管理
 * 用于实时数据推送和协同编辑
 */
export class ScreenWebSocket {
  private ws: WebSocket | null = null
  private url: string
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 3000
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private handlers: Map<string, Array<(data: unknown) => void>> = new Map()
  private isConnected = false

  constructor(url: string) {
    this.url = url
  }

  /**
   * 建立连接
   */
  connect(): void {
    if (this.ws) {
      this.disconnect()
    }

    try {
      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        this.isConnected = true
        this.reconnectAttempts = 0
        this.emit('open', null)
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          const { type, payload } = data
          this.emit(type, payload)
        } catch {
          this.emit('message', event.data)
        }
      }

      this.ws.onclose = () => {
        this.isConnected = false
        this.emit('close', null)
        this.tryReconnect()
      }

      this.ws.onerror = (error) => {
        this.emit('error', error)
      }
    } catch (error) {
      console.error('[ScreenWebSocket] connect error:', error)
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.isConnected = false
  }

  /**
   * 发送消息
   */
  send(type: string, payload: unknown): void {
    if (!this.ws || !this.isConnected) {
      console.warn('[ScreenWebSocket] not connected')
      return
    }

    const message = JSON.stringify({ type, payload })
    this.ws.send(message)
  }

  /**
   * 注册事件处理器
   */
  on(type: string, handler: (data: unknown) => void): void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, [])
    }
    this.handlers.get(type)!.push(handler)
  }

  /**
   * 移除事件处理器
   */
  off(type: string, handler: (data: unknown) => void): void {
    const handlers = this.handlers.get(type)
    if (!handlers) return
    const index = handlers.indexOf(handler)
    if (index >= 0) {
      handlers.splice(index, 1)
    }
  }

  /**
   * 获取连接状态
   */
  getConnected(): boolean {
    return this.isConnected
  }

  /**
   * 触发事件
   */
  private emit(type: string, data: unknown): void {
    const handlers = this.handlers.get(type)
    if (!handlers) return
    handlers.forEach((handler) => {
      try {
        handler(data)
      } catch (error) {
        console.error(`[ScreenWebSocket] handler error for ${type}:`, error)
      }
    })
  }

  /**
   * 尝试重连
   */
  private tryReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('[ScreenWebSocket] max reconnect attempts reached')
      return
    }

    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      console.log(`[ScreenWebSocket] reconnecting (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
      this.connect()
    }, this.reconnectDelay)
  }
}
