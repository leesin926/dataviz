import { BASE_URL } from './request'
import { MOURNING_CONFIG_KEY } from './modules/admin'

/** admin-service 的免登配置推送通道（网关路由没有 StripPrefix，两侧同路径，见 D38） */
const PUSH_CHANNEL_SUFFIX = '/admin/ws/public'

const FIRST_DELAY_MS = 1000
const MAX_DELAY_MS = 30000

export interface PublicConfigFrame {
  key: string
  value: string | null
}

export interface PublicConfigHandlers {
  /** 收到一帧推送；{@code value} 为 null 时按"保持现状"处理是各消费端的既有口径 */
  onData: (frame: PublicConfigFrame) => void
  /** 断线重连成功后补拉一次 —— 去掉轮询后这里是唯一的自愈点 */
  onReconnect: () => void
}

function channelUrl(): string | null {
  if (typeof window === 'undefined') return null
  const url = new URL(`${BASE_URL}${PUSH_CHANNEL_SUFFIX}`, window.location.origin)
  // Electron 打包态是 file:// 页面，相对 BASE_URL 补不出可连的地址 ⇒ 按"没有推送"降级，
  // 而不是构造一个非法 ws:// 让重连计时器空转
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function parseFrame(raw: unknown): PublicConfigFrame | null {
  if (typeof raw !== 'string') return null
  try {
    const obj = JSON.parse(raw) as Partial<PublicConfigFrame> | null
    if (obj && typeof obj.key === 'string') {
      return { key: obj.key, value: typeof obj.value === 'string' ? obj.value : null }
    }
  } catch {
    /* 非 JSON 帧忽略 */
  }
  return null
}

/**
 * 订阅免登全局配置的推送。指数退避重连，首连不触发 {@code onReconnect}
 * （初值由调用方启动时那一次 HTTP 提供，两处各定初值迟早分叉）。
 */
export function subscribePublicConfig(handlers: PublicConfigHandlers): () => void {
  const url = channelUrl()
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
      if (frame) handlers.onData(frame)
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

/**
 * 交给 {@code shared-styles.watchGlobalMourning} 的订阅器：只把哀悼开关那一帧翻译成"值"，
 * 其余键（本通道只回传免登白名单，但未来会加）忽略。签名与 shared-styles 侧靠结构类型对齐，
 * 样式包不依赖 api-client，这个包也不反向依赖样式包。
 */
export function subscribeMourningPush(apply: (value: string | null) => void, refetch: () => void): () => void {
  return subscribePublicConfig({
    onData: (frame) => {
      if (frame.key === MOURNING_CONFIG_KEY) apply(frame.value)
    },
    onReconnect: refetch,
  })
}
