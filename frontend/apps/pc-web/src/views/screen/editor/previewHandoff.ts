import type { Screen, ScreenPlatform } from '@dataviz/shared-types'

/**
 * 编辑器 → 预览页的内存快照通道。
 * 未保存的大屏后端没有记录，预览必须用编辑器的实时草稿，否则预览页空白。
 */
export const DRAFT_PREVIEW_ID = 'draft'

let snapshot: Screen | null = null

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T
}

export function setPreviewDraft(screen: Screen): void {
  snapshot = clone(screen)
}

export function getPreviewDraft(): Screen | null {
  return snapshot
}

export function clearPreviewDraft(): void {
  snapshot = null
}

/** 按投放端展开预览视图：非 pc 端取对应变体，缺省时回退 pc 同源配置 */
export function resolvePreviewScreen(source: Screen, platform: ScreenPlatform): Screen {
  if (platform === 'pc') return source
  const v = source.variants?.[platform]
  if (!v) return source
  return {
    ...source,
    width: v.width,
    height: v.height,
    config: v.config,
    components: v.components || [],
    layers: v.layers || [],
  }
}
