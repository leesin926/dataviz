import type { ChartEvent } from '@dataviz/shared-types'

export interface LinkageGroup {
  id: string
  chartIds: string[]
  dimensionField: string
}

/**
 * 图表联动管理器
 * 当任一图表触发选择事件时,联动其他图表刷新数据
 */
export class LinkageManager {
  private groups: Map<string, LinkageGroup> = new Map()
  private handlers: Map<string, (event: ChartEvent) => void> = new Map()

  /**
   * 注册联动组
   */
  register(group: LinkageGroup): void {
    this.groups.set(group.id, group)
  }

  /**
   * 注销联动组
   */
  unregister(groupId: string): void {
    this.groups.delete(groupId)
    for (const chartId of this.groups.get(groupId)?.chartIds || []) {
      this.handlers.delete(`${groupId}:${chartId}`)
    }
  }

  /**
   * 绑定事件到指定图表实例
   */
  bind(chartId: string, handler: (event: ChartEvent) => void): void {
    this.handlers.set(chartId, handler)
  }

  /**
   * 触发联动 (源图表事件触发后调用)
   */
  trigger(sourceChartId: string, event: ChartEvent): void {
    for (const group of this.groups.values()) {
      if (!group.chartIds.includes(sourceChartId)) continue
      for (const chartId of group.chartIds) {
        if (chartId === sourceChartId) continue
        const h = this.handlers.get(chartId)
        if (h) {
          try {
            h(event)
          } catch (e) {
            console.error(`[linkage] handler error for ${chartId}`, e)
          }
        }
      }
    }
  }
}
