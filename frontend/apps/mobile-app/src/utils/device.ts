/**
 * 设备与布局适配：
 * - phone 布局（手机端/小程序端）：以 rpx 按屏宽等比缩放，横屏/宽屏时内容限宽居中
 * - tablet 布局（平板端）：px 定尺寸 + 内容最大宽度居中 + 列表双栏
 * 各端仅 APP_LAYOUT 常量不同，其余代码一致。
 */

export type DeviceLayout = 'phone' | 'tablet'

/** 本应用的目标布局：mobile-app/mini-program 为 'phone'，tablet-app 为 'tablet' */
const APP_LAYOUT: DeviceLayout = 'phone'

interface SystemInfo {
  windowWidth: number
  windowHeight: number
  pixelRatio: number
  platform?: string
}

const info = uni.getSystemInfoSync() as SystemInfo

export const windowWidth = info.windowWidth
export const windowHeight = info.windowHeight
export const layout: DeviceLayout = APP_LAYOUT
/** 绑定到页面根节点的类名，样式按此区分布局 */
export const layoutClass = `layout-${APP_LAYOUT}`

/** 告警等级/状态中文与配色，Web 三端共用同一份定义 */
export const SEVERITY_META: Record<string, { label: string; color: string }> = {
  CRITICAL: { label: '严重', color: '#f56c6c' },
  WARNING: { label: '警告', color: '#e6a23c' },
  INFO: { label: '提示', color: '#409eff' },
}

export const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: '#f56c6c' },
  ACKNOWLEDGED: { label: '已确认', color: '#e6a23c' },
  RESOLVED: { label: '已解决', color: '#67c23a' },
}

export function severityLabel(severity: string): string {
  return SEVERITY_META[severity]?.label || severity
}

export function severityColor(severity: string): string {
  return SEVERITY_META[severity]?.color || '#909399'
}

export function statusLabel(status: string): string {
  return STATUS_META[status]?.label || status
}

export function statusColor(status: string): string {
  return STATUS_META[status]?.color || '#909399'
}

/** 后端时间可能是 "2026-09-19T10:00:00" 或数组序列化差异，统一成 MM-DD HH:mm */
export function formatTime(value: unknown): string {
  if (!value) return ''
  const date = new Date(value as string | number)
  if (isNaN(date.getTime())) return String(value)
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
