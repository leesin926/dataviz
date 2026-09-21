import type { Screen, ScreenComponent } from '@dataviz/shared-types'

/**
 * 渲染管线: JSON Schema -> 组件树 -> 实际渲染
 */
export class RenderPipeline {
  private cache: Map<string, ScreenComponent[]> = new Map()

  /**
   * 解析大屏 schema 并构建组件渲染列表
   */
  build(screen: Screen): ScreenComponent[] {
    const cached = this.cache.get(String(screen.id))
    if (cached) return cached
    // 按 zIndex 排序
    const sorted = [...screen.components].sort((a, b) => a.zIndex - b.zIndex)
    this.cache.set(String(screen.id), sorted)
    return sorted
  }

  /**
   * 增量更新单个组件
   */
  updateComponent(screenId: string | number, patch: ScreenComponent): void {
    const list = this.cache.get(String(screenId))
    if (!list) return
    const idx = list.findIndex((c) => c.id === patch.id)
    if (idx >= 0) list[idx] = patch
    else list.push(patch)
  }

  /**
   * 清空缓存
   */
  clear(): void {
    this.cache.clear()
  }

  dispose(): void {
    this.clear()
  }
}
