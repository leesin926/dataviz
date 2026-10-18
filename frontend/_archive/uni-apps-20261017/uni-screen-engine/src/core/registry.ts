/**
 * uni 渲染器注册表（4.5）。
 *
 * 与 web 端 `screen-engine/render/componentRegistry.ts` 的区别是**平台逼出来的**：
 * 小程序端不支持 `<component :is="">`（uni 编译期直接报 `<component is=""/> is not supported`），
 * 所以这里解析出来的是**渲染器标识（kind）**而不是组件对象，引擎模板按 kind 静态分支。
 *
 * 后果与取舍：自定义组件在 uni 端只能"映射到某个内置 kind 的渲染器"
 * （`registerUniRenderer('myType', 'chart')`）；要接一个全新的 SFC，只有 H5/App 做得到，
 * 小程序必须静态声明，所以这里不提供注入组件对象的口子——提供了一半能用的 API 更容易误导。
 */

import type { ScreenComponent, ScreenPlatform } from '@dataviz/shared-types'
import { chartPresetOf, isRenderableOnPlatform } from '@dataviz/shared-types'

export type UniRendererKind =
  | 'chart'
  | 'text'
  | 'clock'
  | 'image'
  | 'border'
  | 'table'
  | 'scrollBoard'
  | 'unknown'

const KIND_BY_TYPE = new Map<string, UniRendererKind>([
  ['chart', 'chart'],
  ['text', 'text'],
  ['clock', 'clock'],
  ['image', 'image'],
  ['border', 'border'],
  ['table', 'table'],
  ['scrollBoard', 'scrollBoard'],
])

/** 把某个（含自定义）组件类型映射到已有的内置渲染器 */
export function registerUniRenderer(type: string, kind: UniRendererKind): void {
  KIND_BY_TYPE.set(type, kind)
}

export function resolveUniRenderer(type: string): UniRendererKind | undefined {
  return KIND_BY_TYPE.get(type)
}

export interface ResolvedRenderer {
  kind: UniRendererKind
  /** 图表预设类（barChart/lineChart/… 全部落到 UniChart，由 chartConfig 决定画法） */
  isChart: boolean
}

/**
 * 解析组件在本端的渲染器。
 * 返回 undefined 表示"该端整块跳过"；占位只在调用方明确要求提示时返回。
 */
export function resolveRenderer(
  component: ScreenComponent,
  platform: ScreenPlatform,
  showPlaceholder: boolean,
): ResolvedRenderer | undefined {
  const fallback: ResolvedRenderer | undefined = showPlaceholder ? { kind: 'unknown', isChart: false } : undefined
  if (!isRenderableOnPlatform(component, platform)) return fallback
  if (component.type === 'chart' || chartPresetOf(component.type)) return { kind: 'chart', isChart: true }
  const kind = KIND_BY_TYPE.get(component.type)
  if (kind) return { kind, isChart: false }
  // 白名单过了但没有渲染器 = 引擎缺口，必须显式暴露而不是静默消失
  return fallback
}
