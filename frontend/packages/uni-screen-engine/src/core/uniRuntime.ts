/**
 * uni 运行时适配层。
 *
 * 本包只在 uni 端运行，但 core 下的纯函数要能被 tsc / 单测直接导入，
 * 因此这里对 `uni` 全局做**最小面声明 + 非 uni 环境兜底**，而不是依赖 @dcloudio/types。
 */

export interface UniRequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: unknown
  header?: Record<string, string>
  timeout?: number
}

/** 响应体原样返回（R 结构由调用方解包），非 2xx 与网络失败一律 reject 带文案 */
export type UniRequestFn = (options: UniRequestOptions) => Promise<unknown>

/** uni.connectSocket 返回的 SocketTask 的最小面（只用到被动收帧所需的方法） */
export interface UniSocketTask {
  onOpen(cb: () => void): void
  onMessage(cb: (res: { data: unknown }) => void): void
  onClose(cb: () => void): void
  onError(cb: (err: unknown) => void): void
  close(options?: { code?: number; reason?: string }): void
}

interface UniLike {
  getSystemInfoSync(): { windowWidth?: number; windowHeight?: number; pixelRatio?: number }
  createCanvasContext(canvasId: string, componentInstance?: unknown): unknown
  navigateTo(options: { url: string; fail?: (err: unknown) => void }): void
  setNavigationBarTitle(options: { title: string }): void
  stopPullDownRefresh(): void
  request(
    options: UniRequestOptions & {
      success?: (res: { statusCode: number; data: unknown }) => void
      fail?: (err: unknown) => void
    },
  ): void
  connectSocket(options: { url: string; fail?: (err: unknown) => void }): UniSocketTask
  connectSocket(options: { url: string; fail?: (err: unknown) => void }): UniSocketTask
}

declare const uni: UniLike | undefined

function runtime(): UniLike | null {
  if (typeof uni === 'undefined' || !uni) return null
  return uni as UniLike
}

export function hasUniRuntime(): boolean {
  return runtime() !== null
}

export interface Viewport {
  width: number
  height: number
  pixelRatio: number
}

const FALLBACK_VIEWPORT: Viewport = { width: 375, height: 667, pixelRatio: 2 }

export function getViewport(): Viewport {
  const rt = runtime()
  const info = rt ? rt.getSystemInfoSync() : {}
  const width = Number(info.windowWidth) || 0
  const height = Number(info.windowHeight) || 0
  const ratio = Number(info.pixelRatio) || 0
  if (!width || !height) {
    // 非 uni 环境（类型检查/单测）退回 H5 视口，仍拿不到就用一份手机竖屏常量
    const g = globalThis as unknown as { window?: { innerWidth: number; innerHeight: number; devicePixelRatio: number } }
    if (g.window && g.window.innerWidth) {
      return {
        width: g.window.innerWidth,
        height: g.window.innerHeight,
        pixelRatio: g.window.devicePixelRatio || 2,
      }
    }
    return FALLBACK_VIEWPORT
  }
  return { width, height, pixelRatio: ratio || 2 }
}

/** 默认请求实现：宿主 App 可注入自己的（带网关前缀、token、错误口径）替代 */
export const uniRequest: UniRequestFn = (options) => {
  const rt = runtime()
  if (!rt) return Promise.reject(new Error('uni.request 不可用：uni-screen-engine 仅运行于 uni 端'))
  return new Promise((resolve, reject) => {
    rt.request({
      ...options,
      timeout: options.timeout || 15000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data)
        else reject(new Error(`请求失败(${res.statusCode}) ${options.url}`))
      },
      fail: () => reject(new Error(`网络错误：${options.url}`)),
    })
  })
}

/** 默认 SocketTask 工厂：非 uni 环境（类型检查/单测）返回 null，由调用方按"没有推送"降级 */
export function uniConnectSocket(url: string): UniSocketTask | null {
  const rt = runtime()
  if (!rt) return null
  try {
    return rt.connectSocket({ url })
  } catch (e) {
    console.warn('[uni-screen-engine] connectSocket 失败', url, e)
    return null
  }
}

export function createCanvasContext(canvasId: string, componentInstance?: unknown): unknown | null {
  const rt = runtime()
  if (!rt) return null
  return rt.createCanvasContext(canvasId, componentInstance)
}

/** 页面跳转/导航栏由共用页组件调用，app 侧不必再包一层 */
export function navigateTo(url: string): void {
  const rt = runtime()
  if (!rt) return
  rt.navigateTo({
    url,
    fail: (err) => console.warn('[uni-screen-engine] navigateTo 失败', url, err),
  })
}

export function setNavigationBarTitle(title: string): void {
  const rt = runtime()
  if (rt && title) rt.setNavigationBarTitle({ title })
}

export function stopPullDownRefresh(): void {
  const rt = runtime()
  if (rt) rt.stopPullDownRefresh()
}
