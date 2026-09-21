import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'

export interface ChartDataInput {
  dimensions: string[]
  series: Array<{ name: string; data: number[] }>
}

/**
 * BarChart - 柱状图选项生成器
 */
export class BarChart {
  static buildOption(config: ChartConfig, data: ChartDataInput): EChartsOption {
    const isStack = !!config.options?.stack
    const isHorizontal = !!config.options?.horizontal

    const xAxis: EChartsOption['xAxis'] = isHorizontal
      ? { type: 'value', boundaryGap: [0, 0.01] }
      : {
          type: 'category',
          data: data.dimensions,
          axisLabel: { rotate: data.dimensions.length > 10 ? 30 : 0 },
        }

    const yAxis: EChartsOption['yAxis'] = isHorizontal
      ? { type: 'category', data: data.dimensions }
      : { type: 'value', boundaryGap: [0, 0.01] }

    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
      grid: { left: 40, right: 20, top: 50, bottom: 40, containLabel: true },
      xAxis,
      yAxis,
      series: data.series.map((s) => ({
        name: s.name,
        type: 'bar' as const,
        data: s.data,
        stack: isStack ? 'total' : undefined,
        barMaxWidth: 40,
        emphasis: { focus: 'series' as const },
      })),
    }
  }
}
