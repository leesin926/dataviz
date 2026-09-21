import type { ChartConfig } from '@dataviz/shared-types'
import { ChartType } from '@dataviz/shared-types'

/**
 * 图表配置适配器: 规范化与默认值填充
 */
export class ChartConfigAdapter {
  static normalize(config: ChartConfig): ChartConfig {
    const normalized: ChartConfig = {
      ...config,
      dimensions: config.dimensions || [],
      measures: config.measures || [],
      filters: config.filters || [],
      sorts: config.sorts || [],
      options: config.options || {},
      theme: config.theme || 'light',
      animation: config.animation ?? true,
    }
    // 图表类型默认值处理
    if (!normalized.chartType) {
      normalized.chartType = ChartType.BAR
    }
    // 度量默认聚合
    normalized.measures = normalized.measures.map((m) => ({
      ...m,
      aggregation: m.aggregation || 'sum',
    }))
    return normalized
  }

  /**
   * 根据数据特征推荐图表类型
   */
  static recommend(dimensions: number, measures: number, rowCount: number): ChartType {
    if (measures === 1 && dimensions === 0) return ChartType.KPI_CARD
    if (dimensions === 0 && measures >= 2) return ChartType.BAR
    if (dimensions === 1 && measures === 1) {
      if (rowCount > 100) return ChartType.SCATTER
      return ChartType.BAR
    }
    if (dimensions === 1 && measures >= 2) return ChartType.LINE
    if (dimensions === 2 && measures === 1) return ChartType.BAR
    if (dimensions >= 2 && measures >= 1) return ChartType.TREEMAP
    return ChartType.TABLE
  }
}
