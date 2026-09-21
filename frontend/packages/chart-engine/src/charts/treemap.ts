import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 矩形树图选项构建
 */
export function buildTreemapOption(config: ChartConfig, data: ChartData): EChartsOption {
  const treeData = data.dimensions.map((dim, i) => ({
    name: dim,
    value: data.series[0]?.data[i] as number,
  }))

  return {
    tooltip: { formatter: '{b}: {c}' },
    series: [
      {
        type: 'treemap' as const,
        data: treeData,
        label: { show: true, formatter: '{b}\n{c}' },
        upperLabel: { show: true, height: 24 },
        levels: [
          { itemStyle: { borderWidth: 2, borderColor: '#fff', gapWidth: 2 } },
        ],
      },
    ],
  }
}
