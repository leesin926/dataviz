import type { ScreenComponent } from '@dataviz/shared-types'
import type { DataEnv } from '../core/dataSource'

/**
 * 渲染器统一入参：引擎对每个组件都传这一份，
 * 组件按需要取用 —— 不声明的字段会变成 attribute 落到根节点上，所以宁可统一声明。
 */
export interface UniRendererProps {
  component: ScreenComponent
  /** 设备像素宽（canvas 类组件需要真实像素） */
  width?: number
  /** 设备像素高 */
  height?: number
  /** 设计稿 → 设备的比例，组件内部字号/线宽按它换算 */
  scale?: number
  /** 取数环境（static/dataset/http 三类数据源用得上） */
  env?: DataEnv
}

/** 渲染器被单独使用（不在引擎里）时的兜底取数环境 */
export const DEFAULT_ENV: DataEnv = { baseUrl: '/api' }

export function numOf(value: unknown, fallback: number): number {
  const n = typeof value === 'number' ? value : Number(String(value ?? '').trim())
  return Number.isFinite(n) ? n : fallback
}

export function strOf(value: unknown, fallback: string): string {
  return typeof value === 'string' && value ? value : fallback
}
