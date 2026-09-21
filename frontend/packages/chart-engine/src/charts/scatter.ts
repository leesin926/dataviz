import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 散点图选项构建
 */
export function buildScatterOption(config: ChartConfig, data: ChartData): EChartsOption {
  const showSize = data.measures.length >= 3

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const p = params as { value: unknown[]; seriesName: string }
        return `${p.seriesName}<br/>${data.measures[0]}: ${p.value?.[0]}<br/>${data.measures[1]}: ${p.value?.[1]}`
      },
    },
    legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
    grid: { left: 40, right: 20, top: 50, bottom: 40, containLabel: true },
    xAxis: { type: 'value', name: data.measures[0], nameLocation: 'middle', nameGap: 30 },
    yAxis: { type: 'value', name: data.measures[1] },
    series: data.series.map((s) => ({
      name: s.name,
      type: 'scatter' as const,
      data: s.data as unknown[],
      symbolSize: showSize ? 20 : 10,
      emphasis: { focus: 'series' as const },
    })),
  }
}
