import './tokens.css'
import './base.css'
import './element.css'

/** 哀悼模式挂在根容器上的类名 */
export const MOURNING_CLASS = 'dv-mourning'

/**
 * 全局哀悼模式开关：由管理端系统配置 screen.mourning.enabled 驱动，
 * 各端启动时按配置在 <html> 上挂/摘类名，整页（含设计器与管理端）一起灰度。
 */
export function setGlobalMourning(on: boolean): void {
  if (typeof document === 'undefined') return
  document.documentElement.classList.toggle(MOURNING_CLASS, on)
}

/**
 * 订阅器由调用方注入：把「值变了」回调和「重连后补拉一次」回调交给传输层（api-client 的推送通道）。
 * 返回的清理函数这里不用管，各端生命周期就是整个页面。
 */
export type MourningSubscriber = (apply: (value: string | null) => void, refetch: () => void) => () => void

/**
 * 各端启动时接入管理端的全局哀悼模式：`fetchEnabled` 与 `subscribe` 都由调用方注入（值取 'true'/'false'/null），
 * shared-styles 不依赖 api-client 以免样式包反向依赖请求层。
 * 口径是**首屏一次 HTTP 定初值，之后纯推送**：不再挂 focus / visibilitychange 重复拉取，
 * 那条回头路由由订阅器在重连后回调 `fetchEnabled` 承担。
 * 拉取失败（网关未升级、401、断网）时保持现状，不强制关闭，否则会覆盖管理端本地刚切上的开关。
 */
export function watchGlobalMourning(
  fetchEnabled: () => Promise<string | null>,
  subscribe: MourningSubscriber,
): void {
  if (typeof document === 'undefined') return
  const apply = (value: string | null): void => {
    if (value === 'true' || value === 'false') setGlobalMourning(value === 'true')
  }
  const sync = (): void => {
    fetchEnabled()
      .then(apply)
      .catch(() => undefined)
  }
  sync()
  subscribe(apply, sync)
}

/** 暗色科技主题开关：document.documentElement.dataset.dvTheme */
export const THEME_ATTR = 'data-dv-theme'

/** 设计令牌的 JS 侧镜像（供 ECharts 主题、canvas 绘制等消费） */
export const DESIGN_TOKENS = {
  primary: '#2f7cff',
  accent: '#22b8ff',
  success: '#23b98a',
  warning: '#f5a524',
  danger: '#f25555',
  screenAccent: '#3fdaff',
  screenBg: '#0a1929',
  screenText: '#e0e6ed',
  fontFamily: "'Inter', 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif",
} as const

/** 图表默认色板（各端统一） */
export const CHART_PALETTE = [
  '#22b8ff',
  '#2f7cff',
  '#23b98a',
  '#f5a524',
  '#f25555',
  '#7c5cff',
  '#00c2a8',
  '#ff8f4d',
] as const
