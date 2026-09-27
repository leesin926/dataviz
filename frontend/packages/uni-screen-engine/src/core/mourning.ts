/**
 * 全局哀悼模式（uni 端）。
 *
 * Web 端靠 shared-styles 在 `<html>` 上挂 `.dv-mourning` 类，但小程序/App 没有 document，
 * 所以 uni 端只能由渲染器自己把配置值翻成 `filter: grayscale(100%)`。
 * 开关的**唯一来源仍是管理端系统配置** `screen.mourning.enabled`（免登端点），
 * 大屏自带的 `config.grayMode` 已废弃，不再作为输入。
 *
 * 生效口径与 web 端一致：**首屏一次 HTTP 定初值，之后纯推送**（`watchGlobalMourningPush`）。
 */

import { ref } from 'vue'
import type { Ref } from 'vue'
import { uniConnectSocket, uniRequest, type UniRequestFn, type UniSocketTask } from './uniRuntime'

export const MOURNING_CONFIG_KEY = 'screen.mourning.enabled'

/** 响应式全局灰度开关，渲染器读取它 */
export const globalMourning: Ref<boolean> = ref(false)

const PUSH_CHANNEL_SUFFIX = '/admin/ws/public'
const FIRST_DELAY_MS = 1000
const MAX_DELAY_MS = 30000

/** 只认 'true'/'false' 两个字面量；其余（null、脏值、错误帧）保持现状，不强制关闭 */
function applyValue(value: unknown): void {
  if (value === 'true' || value === 'false') globalMourning.value = value === 'true'
}

/**
 * 拉取一次全局开关。失败保持现状（与 web 端口径一致）：
 * 网关没升级/断网时若强制 false，会把管理端刚切上的开关就地关掉。
 */
export async function refreshGlobalMourning(baseUrl: string, request?: UniRequestFn): Promise<boolean> {
  const send = request || uniRequest
  try {
    const url = `${baseUrl}/admin/config/public/${encodeURIComponent(MOURNING_CONFIG_KEY)}`
    const body = (await send({ url })) as { code?: number; data?: string | null }
    const value = body?.code === 0 || body?.code === 200 ? body.data : null
    applyValue(value)
  } catch {
    /* 保持现状 */
  }
  return globalMourning.value
}

function pushUrl(baseUrl: string): string | null {
  const path = `${baseUrl}${PUSH_CHANNEL_SUFFIX}`
  const absolute = /^(https?):\/\/(.+)$/.exec(path)
  if (absolute) return (absolute[1] === 'https' ? 'wss://' : 'ws://') + absolute[2]
  // 相对地址只有 H5 能补出 host；小程序/App 的 baseUrl 本来就是绝对地址，补不出就不连
  const loc = (globalThis as { location?: { protocol?: string; host?: string } }).location
  if (!loc || !loc.host) return null
  return (loc.protocol === 'https:' ? 'wss://' : 'ws://') + loc.host + path
}

function frameKeyAndValue(raw: unknown): { key: unknown; value: unknown } | null {
  if (typeof raw !== 'string') return null
  try {
    const obj = JSON.parse(raw) as { key?: unknown; value?: unknown } | null
    return obj && typeof obj.key === 'string' ? { key: obj.key, value: obj.value } : null
  } catch {
    return null
  }
}

/**
 * 订阅免登推送通道（指数退避重连，1s 起、30s 封顶），返回取消订阅的函数。
 * 去掉轮询之后**重连补拉是这条链唯一的自愈点**：首连不补拉（初值已由 `refreshGlobalMourning` 给过），
 * 之后每次重新打开都补拉一次，避免"服务端漏推一帧就永久错态"。
 */
export function watchGlobalMourningPush(
  baseUrl: string,
  options: { request?: UniRequestFn; connect?: (url: string) => UniSocketTask | null } = {},
): () => void {
  const send = options.request || uniRequest
  const connect = options.connect || uniConnectSocket
  const url = pushUrl(baseUrl)
  if (!url) return () => undefined

  let task: UniSocketTask | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let delayMs = FIRST_DELAY_MS
  let stopped = false
  let openedOnce = false

  const schedule = () => {
    if (stopped || timer) return
    const wait = Math.min(delayMs, MAX_DELAY_MS)
    delayMs = wait * 2
    timer = setTimeout(() => {
      timer = null
      open()
    }, wait)
  }

  const open = () => {
    if (stopped || task) return
    const current = connect(url)
    if (!current) {
      schedule()
      return
    }
    task = current
    current.onOpen(() => {
      delayMs = FIRST_DELAY_MS
      if (openedOnce) void refreshGlobalMourning(baseUrl, send)
      openedOnce = true
    })
    current.onMessage((res) => {
      const frame = frameKeyAndValue(res.data)
      if (frame && frame.key === MOURNING_CONFIG_KEY) applyValue(frame.value)
    })
    current.onClose(() => {
      if (task === current) task = null
      schedule()
    })
    // onError 之后一定紧跟 onClose，重连统一交给它，免得排两次
    current.onError(() => undefined)
  }

  open()

  return () => {
    stopped = true
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    const current = task
    task = null
    if (current) current.close({ code: 1000 })
  }
}
