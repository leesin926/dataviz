import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 折线图选项构建
 */
export function buildLineOption(config: ChartConfig, data: ChartData): EChartsOption {
  const smooth = config.options?.smooth !== false
  const showArea = !!config.options?.area
  const showSymbol = config.options?.symbol !== false

  return {
    tooltip: { trigger: 'axis' },
    legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
    grid: { left: 40, right: 20, top: 50, bottom: 40, containLabel: true },
    xAxis: { type: 'category', data: data.dimensions, boundaryGap: false },
    yAxis: { type: 'value' },
    series: data.series.map((s) => ({
      name: s.name,
      type: 'line' as const,
      data: s.data,
      smooth,
      showSymbol,
      areaStyle: showArea ? { opacity: 0.3 } : undefined,
      emphasis: { focus: 'series' as const },
    })),
  }
}
