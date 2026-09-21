import type { ScreenComponent } from '@dataviz/shared-types'

/**
 * 画布管理系统
 * 负责组件绝对定位、拖拽、缩放、图层
 */
export class CanvasSystem {
  private components: ScreenComponent[] = []
  private selectedId: string | null = null
  private gridSize = 8

  constructor(initialComponents: ScreenComponent[] = []) {
    this.components = initialComponents
  }

  getComponents(): ScreenComponent[] {
    return this.components
  }

  getComponent(id: string): ScreenComponent | undefined {
    return this.components.find((c) => c.id === id)
  }

  addComponent(comp: ScreenComponent): void {
    this.components.push(comp)
  }

  removeComponent(id: string): void {
    this.components = this.components.filter((c) => c.id !== id)
    if (this.selectedId === id) this.selectedId = null
  }

  updateComponent(id: string, patch: Partial<ScreenComponent>): void {
    const c = this.components.find((x) => x.id === id)
    if (c) Object.assign(c, patch)
  }

  select(id: string | null): void {
    this.selectedId = id
  }

  getSelected(): ScreenComponent | null {
    return this.components.find((c) => c.id === this.selectedId) || null
  }

  /**
   * 拖拽时计算新坐标
   */
  moveComponent(id: string, dx: number, dy: number): void {
    const c = this.getComponent(id)
    if (!c || c.locked) return
    c.x = Math.max(0, c.x + dx)
    c.y = Math.max(0, c.y + dy)
  }

  /**
   * 缩放时计算新尺寸
   */
  resizeComponent(id: string, dw: number, dh: number): void {
    const c = this.getComponent(id)
    if (!c || c.locked) return
    c.w = Math.max(20, c.w + dw)
    c.h = Math.max(20, c.h + dh)
  }

  setGridSize(size: number): void {
    this.gridSize = size
  }

  getGridSize(): number {
    return this.gridSize
  }
}
