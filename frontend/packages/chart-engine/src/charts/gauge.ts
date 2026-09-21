import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 仪表盘选项构建
 */
export function buildGaugeOption(config: ChartConfig, data: ChartData): EChartsOption {
  const max = (config.options?.max as number) ?? 100
  const min = (config.options?.min as number) ?? 0
  const value = (data.series[0]?.data[0] as number) ?? 0
  const title = config.title || data.measures[0] || '数值'

  return {
    tooltip: { formatter: '{a} <br/>{b}: {c}' },
    series: [
      {
        type: 'gauge' as const,
        min,
        max,
        progress: { show: true, width: 14 },
        axisLine: { lineStyle: { width: 14 } },
        axisTick: { show: false },
        splitLine: { length: 10, lineStyle: { width: 2 } },
        axisLabel: { distance: 20, fontSize: 11 },
        detail: { valueAnimation: true, formatter: '{value}', fontSize: 24 },
        data: [{ value, name: title }],
        title: { offsetCenter: [0, '70%'], fontSize: 14 },
      },
    ],
  }
}
