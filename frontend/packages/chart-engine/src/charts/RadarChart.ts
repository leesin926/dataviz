import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'

export interface RadarDataInput {
  indicators: Array<{ name: string; max: number }>
  series: Array<{ name: string; data: number[] }>
}

/**
 * RadarChart - 雷达图选项生成器
 */
export class RadarChart {
  static buildOption(config: ChartConfig, data: RadarDataInput): EChartsOption {
    const isArea = config.options?.area !== false

    return {
      tooltip: { trigger: 'item' },
      legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
      radar: {
        indicator: data.indicators,
        shape: 'circle',
        splitNumber: 5,
        axisName: { color: '#999' },
        splitArea: {
          areaStyle: {
            color: ['rgba(255,255,255,0.1)', 'rgba(200,200,200,0.1)'],
          },
        },
      },
      series: [
        {
          type: 'radar' as const,
          data: data.series.map((s) => ({
            name: s.name,
            value: s.data,
            areaStyle: isArea ? {} : undefined,
            emphasis: { lineStyle: { width: 3 } },
          })),
        },
      ],
    }
  }
}
