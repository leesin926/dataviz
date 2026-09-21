import type { ChartConfig, ChartFilter } from '@dataviz/shared-types'

export interface DrillState {
  stack: DrillLevel[]
  currentIndex: number
}

export interface DrillLevel {
  dimensionField: string
  filterValue?: string | number
  label: string
}

/**
 * 下钻管理器
 * 支持在维度上逐级下钻: 点击某个类别时,过滤并展示下一层维度
 */
export class DrillDownManager {
  private state: DrillState = { stack: [], currentIndex: -1 }
  private dimensions: string[] = []
  private onChange: ((state: DrillState) => void) | null = null

  /**
   * 设置下钻维度序列 (e.g. ['省', '市', '区'])
   */
  configure(dimensions: string[], onChange: (state: DrillState) => void): void {
    this.dimensions = dimensions
    this.onChange = onChange
    this.reset()
  }

  /**
   * 重置下钻状态
   */
  reset(): void {
    this.state = { stack: [], currentIndex: -1 }
    this.onChange?.(this.state)
  }

  /**
   * 下钻到下一层
   */
  drillDown(value: string | number, label: string): void {
    const nextIdx = this.state.currentIndex + 1
    if (nextIdx >= this.dimensions.length) return
    const level: DrillLevel = {
      dimensionField: this.dimensions[nextIdx],
      filterValue: value,
      label,
    }
    const newStack = [...this.state.stack, level]
    this.state = { stack: newStack, currentIndex: nextIdx }
    this.onChange?.(this.state)
  }

  /**
   * 返回上一层
   */
  drillUp(): void {
    if (this.state.currentIndex < 0) return
    const newStack = this.state.stack.slice(0, -1)
    this.state = { stack: newStack, currentIndex: this.state.currentIndex - 1 }
    this.onChange?.(this.state)
  }

  /**
   * 根据当前下钻状态生成过滤器
   */
  buildFilters(): ChartFilter[] {
    return this.state.stack
      .filter((lv) => lv.filterValue !== undefined)
      .map((lv) => ({
        field: lv.dimensionField,
        operator: '=',
        value: lv.filterValue,
      }))
  }

  getState(): DrillState {
    return this.state
  }
}
