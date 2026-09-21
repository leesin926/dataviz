import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 雷达图选项构建
 */
export function buildRadarOption(config: ChartConfig, data: ChartData): EChartsOption {
  const maxVal = Math.max(
    ...data.series.flatMap((s) => s.data as number[]).filter((v) => typeof v === 'number'),
    1,
  )
  const indicator = data.dimensions.map((dim) => ({ name: dim, max: maxVal * 1.2 }))

  return {
    tooltip: { trigger: 'item' },
    legend: data.series.length > 1 ? { data: data.series.map((s) => s.name) } : undefined,
    radar: { indicator, shape: 'polygon', splitNumber: 5 },
    series: [
      {
        type: 'radar' as const,
        data: data.series.map((s) => ({
          name: s.name,
          value: s.data,
          areaStyle: { opacity: 0.2 },
        })),
      },
    ],
  }
}
