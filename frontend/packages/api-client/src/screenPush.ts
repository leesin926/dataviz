import { BASE_URL } from './request'

/** screen-service 的分享页变更推送通道前缀（网关路由 /api/screen/** 不带 StripPrefix，两侧同路径） */
const CHANNEL_PREFIX = '/screen/ws/share/'

const FIRST_DELAY_MS = 1000
const MAX_DELAY_MS = 30000

export type ScreenChangeReason = 'updated' | 'published' | 'unpublished' | 'deleted'

export interface ScreenChangeFrame {
  screenId: number | null
  reason: ScreenChangeReason | string
  time: number
}

export interface ScreenChangeHandlers {
  /** 收到一帧"这张大屏变了"；调用方回去重拉配置，本模块不猜该怎么合并 */
  onChanged: (frame: ScreenChangeFrame) => void
  /** 断线重连成功后补拉一次 —— 没有轮询兜底时这里是唯一的自愈点 */
  onReconnect: () => void
}

function channelUrl(shareToken: string): string | null {
  if (typeof window === 'undefined' || !shareToken) return null
  const url = new URL(`${BASE_URL}${CHANNEL_PREFIX}${encodeURIComponent(shareToken)}`, window.location.origin)
  // Electron/uni 打包态可能是 file:// 页面，相对 BASE_URL 补不出可连的地址 ⇒ 按"没有推送"降级，
  // 而不是构造一个非法 ws:// 让重连计时器空转
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function parseFrame(raw: unknown): ScreenChangeFrame | null {
  if (typeof raw !== 'string') return null
  try {
    const obj = JSON.parse(raw) as Partial<ScreenChangeFrame> | null
    if (obj && typeof obj.reason === 'string') {
      return {
        screenId: typeof obj.screenId === 'number' ? obj.screenId : null,
        reason: obj.reason,
        time: typeof obj.time === 'number' ? obj.time : 0,
      }
    }
  } catch {
    /* 非 JSON 帧（例如那帧 push-only 错误）忽略 */
  }
  return null
}

/**
 * 订阅某张大屏的变更推送。
 * <p>订阅键是分享令牌，不是大屏 id：这条通道免登，令牌就是"我拿得到这张大屏"的凭证，
 * 与 {@code getSharedScreen} 用的那一个同源。用 id 订阅等于要求访客先知道内部主键。</p>
 * <p>指数退避重连；首连不触发 {@code onReconnect}（初值由调用方那次 HTTP 提供，两处各定初值迟早分叉）。</p>
 */
export function subscribeScreenChanges(shareToken: string, handlers: ScreenChangeHandlers): () => void {
  const url = channelUrl(shareToken)
  if (!url || typeof WebSocket === 'undefined') return () => undefined

  let socket: WebSocket | null = null
  let timer: number | null = null
  let delayMs = FIRST_DELAY_MS
  let stopped = false
  let openedOnce = false

  const clearTimer = () => {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  const scheduleReconnect = () => {
    if (stopped || timer !== null) return
    const wait = Math.min(delayMs, MAX_DELAY_MS)
    delayMs = wait * 2
    timer = window.setTimeout(() => {
      timer = null
      open()
    }, wait)
  }

  const open = () => {
    if (stopped || socket || !url) return
    if (typeof document !== 'undefined' && document.hidden) {
      // 后台标签页不重连：隐藏时收帧没意义，重新可见由下面的 nudge 立刻补上
      return
    }
    try {
      socket = new WebSocket(url)
    } catch {
      socket = null
      scheduleReconnect()
      return
    }
    const current = socket
    current.onopen = () => {
      delayMs = FIRST_DELAY_MS
      if (openedOnce) handlers.onReconnect()
      openedOnce = true
    }
    current.onmessage = (event: MessageEvent) => {
      const frame = parseFrame(event.data)
      if (frame) handlers.onChanged(frame)
    }
    current.onclose = () => {
      if (socket === current) socket = null
      scheduleReconnect()
    }
    current.onerror = () => {
      // onerror 之后紧跟 onclose，重连交给 onclose，免得排两次
      if (socket === current) socket.close()
    }
  }

  const nudge = () => {
    clearTimer()
    delayMs = FIRST_DELAY_MS
    open()
  }

  const onVisibility = () => {
    if (typeof document !== 'undefined' && !document.hidden) nudge()
  }

  window.addEventListener('online', nudge)
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onVisibility)

  open()

  return () => {
    stopped = true
    clearTimer()
    window.removeEventListener('online', nudge)
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisibility)
    const closing = socket
    socket = null
    if (closing) {
      closing.onopen = null
      closing.onmessage = null
      closing.onclose = null
      closing.onerror = null
      try {
        closing.close()
      } catch {
        /* 已经断开 */
      }
    }
  }
}
