import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 饼图选项构建
 */
export function buildPieOption(config: ChartConfig, data: ChartData): EChartsOption {
  const isRose = !!config.options?.rose
  const isDonut = !!config.options?.donut

  const pieData = data.dimensions.map((dim, i) => ({
    name: dim,
    value: data.series[0]?.data[i] as number,
  }))

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [
      {
        type: 'pie' as const,
        radius: isDonut ? ['40%', '70%'] : isRose ? [20, 100] : '70%',
        center: ['40%', '50%'],
        roseType: isRose ? 'area' : undefined,
        data: pieData,
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' },
        },
        label: { show: true, formatter: '{b}: {d}%' },
      },
    ],
  }
}
