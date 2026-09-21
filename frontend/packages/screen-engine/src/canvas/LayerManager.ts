import type { ScreenComponent, ScreenLayer } from '@dataviz/shared-types'

/**
 * 图层管理
 */
export class LayerManager {
  private layers: ScreenLayer[] = []

  constructor(initial: ScreenLayer[] = []) {
    this.layers = initial
  }

  getLayers(): ScreenLayer[] {
    return this.layers
  }

  addLayer(layer: ScreenLayer): void {
    this.layers.push(layer)
  }

  removeLayer(id: string): void {
    this.layers = this.layers.filter((l) => l.id !== id)
  }

  toggleVisibility(id: string): void {
    const l = this.layers.find((x) => x.id === id)
    if (l) l.visible = !l.visible
  }

  toggleLock(id: string): void {
    const l = this.layers.find((x) => x.id === id)
    if (l) l.locked = !l.locked
  }

  moveUp(id: string): void {
    const idx = this.layers.findIndex((l) => l.id === id)
    if (idx < 0 || idx >= this.layers.length - 1) return
    ;[this.layers[idx], this.layers[idx + 1]] = [this.layers[idx + 1], this.layers[idx]]
  }

  moveDown(id: string): void {
    const idx = this.layers.findIndex((l) => l.id === id)
    if (idx <= 0) return
    ;[this.layers[idx], this.layers[idx - 1]] = [this.layers[idx - 1], this.layers[idx]]
  }

  /**
   * 根据组件所在图层返回其可见性
   */
  isComponentVisible(compId: string): boolean {
    for (const l of this.layers) {
      if (!l.visible) continue
      if (l.componentIds.includes(compId)) return true
    }
    return true
  }

  /**
   * 按层级重新计算所有组件的 zIndex
   */
  recomputeZIndex(components: ScreenComponent[]): void {
    let z = 1
    for (const layer of this.layers) {
      if (!layer.visible) continue
      for (const compId of layer.componentIds) {
        const c = components.find((x) => x.id === compId)
        if (c) c.zIndex = z++
      }
    }
  }
}
