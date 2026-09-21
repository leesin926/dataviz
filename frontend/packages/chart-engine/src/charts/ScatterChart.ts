import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'

export interface ScatterDataInput {
  series: Array<{
    name: string
    data: Array<[number, number] | [number, number, number]>
  }>
}

/**
 * ScatterChart - 散点图选项生成器
 */
export class ScatterChart {
  static buildOption(config: ChartConfig, data: ScatterDataInput): EChartsOption {
    const isBubble = !!config.options?.bubble

    return {
      tooltip: {
        trigger: 'item',
        formatter: (params: unknown) => {
          const p = params as { seriesName: string; value: number[] }
          return `${p.seriesName}: (${p.value.join(', ')})`
        },
      },
      legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
      grid: { left: 40, right: 20, top: 50, bottom: 40, containLabel: true },
      xAxis: { type: 'value', scale: true },
      yAxis: { type: 'value', scale: true },
      series: data.series.map((s) => ({
        name: s.name,
        type: 'scatter' as const,
        data: s.data,
        symbolSize: isBubble
          ? (val: number[]) => Math.sqrt(val[2] || 1) * 5
          : 10,
        emphasis: { focus: 'series' as const },
      })),
    }
  }
}
