import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartDataInput } from './BarChart'

/**
 * LineChart - 折线图选项生成器
 */
export class LineChart {
  static buildOption(config: ChartConfig, data: ChartDataInput): EChartsOption {
    const isSmooth = config.options?.smooth !== false
    const isArea = !!config.options?.areaStyle

    return {
      tooltip: { trigger: 'axis' },
      legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
      grid: { left: 40, right: 20, top: 50, bottom: 40, containLabel: true },
      xAxis: {
        type: 'category',
        data: data.dimensions,
        boundaryGap: false,
      },
      yAxis: { type: 'value' },
      series: data.series.map((s) => ({
        name: s.name,
        type: 'line' as const,
        data: s.data,
        smooth: isSmooth,
        areaStyle: isArea ? {} : undefined,
        emphasis: { focus: 'series' as const },
      })),
    }
  }
}
