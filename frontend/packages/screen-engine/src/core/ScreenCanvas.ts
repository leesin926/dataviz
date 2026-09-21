import type { ScreenComponent, ScreenLayer } from '@dataviz/shared-types'

/**
 * ScreenCanvas - 大屏画布管理系统
 * 负责组件管理、图层系统、选中状态
 */
export class ScreenCanvas {
  private components: Map<string, ScreenComponent> = new Map()
  private layers: ScreenLayer[] = []
  private selectedIds: Set<string> = new Set()
  private gridSize: number = 8
  private gridSnap: boolean = true

  constructor() {
    // 默认图层
    this.layers.push({
      id: 'default',
      name: '默认图层',
      visible: true,
      locked: false,
      componentIds: [],
    })
  }

  /**
   * 添加组件
   */
  addComponent(component: ScreenComponent): void {
    this.components.set(component.id, component)
    const layer = this.layers[this.layers.length - 1]
    if (layer) {
      layer.componentIds.push(component.id)
    }
  }

  /**
   * 移除组件
   */
  removeComponent(id: string): void {
    this.components.delete(id)
    this.selectedIds.delete(id)
    this.layers.forEach((layer) => {
      layer.componentIds = layer.componentIds.filter((cid) => cid !== id)
    })
  }

  /**
   * 获取组件
   */
  getComponent(id: string): ScreenComponent | undefined {
    return this.components.get(id)
  }

  /**
   * 获取所有组件
   */
  getComponents(): ScreenComponent[] {
    return Array.from(this.components.values())
  }

  /**
   * 更新组件
   */
  updateComponent(id: string, patch: Partial<ScreenComponent>): void {
    const component = this.components.get(id)
    if (component) {
      Object.assign(component, patch)
    }
  }

  /**
   * 选中组件
   */
  select(id: string, multi = false): void {
    if (!multi) {
      this.selectedIds.clear()
    }
    this.selectedIds.add(id)
  }

  /**
   * 取消选中
   */
  deselect(id: string): void {
    this.selectedIds.delete(id)
  }

  /**
   * 清空选中
   */
  clearSelection(): void {
    this.selectedIds.clear()
  }

  /**
   * 获取选中组件
   */
  getSelectedComponents(): ScreenComponent[] {
    return Array.from(this.selectedIds)
      .map((id) => this.components.get(id))
      .filter((c): c is ScreenComponent => c !== undefined)
  }

  /**
   * 添加图层
   */
  addLayer(layer: ScreenLayer): void {
    this.layers.push(layer)
  }

  /**
   * 获取图层
   */
  getLayers(): ScreenLayer[] {
    return this.layers
  }

  /**
   * 设置网格
   */
  setGrid(size: number, snap: boolean): void {
    this.gridSize = size
    this.gridSnap = snap
  }

  /**
   * 对齐到网格
   */
  snapToGrid(value: number): number {
    if (!this.gridSnap) return value
    return Math.round(value / this.gridSize) * this.gridSize
  }

  /**
   * 移动组件
   */
  moveComponent(id: string, x: number, y: number): void {
    const component = this.components.get(id)
    if (component && !component.locked) {
      component.x = this.gridSnap ? this.snapToGrid(x) : x
      component.y = this.gridSnap ? this.snapToGrid(y) : y
    }
  }

  /**
   * 调整组件大小
   */
  resizeComponent(id: string, width: number, height: number): void {
    const component = this.components.get(id)
    if (component && !component.locked) {
      component.w = this.gridSnap ? this.snapToGrid(width) : width
      component.h = this.gridSnap ? this.snapToGrid(height) : height
    }
  }

  /**
   * 调整组件层级
   */
  setComponentZIndex(id: string, zIndex: number): void {
    const component = this.components.get(id)
    if (component) {
      component.zIndex = zIndex
    }
  }

  /**
   * 导出画布数据
   */
  export(): { components: ScreenComponent[]; layers: ScreenLayer[] } {
    return {
      components: this.getComponents(),
      layers: this.layers,
    }
  }

  /**
   * 导入画布数据
   */
  import(data: { components: ScreenComponent[]; layers: ScreenLayer[] }): void {
    this.components.clear()
    data.components.forEach((c) => this.components.set(c.id, c))
    this.layers = data.layers.length > 0 ? data.layers : this.layers
  }
}
