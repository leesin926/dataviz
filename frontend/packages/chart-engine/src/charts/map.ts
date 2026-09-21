import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 地图选项构建
 */
export function buildMapOption(config: ChartConfig, data: ChartData): EChartsOption {
  const mapName = (config.options?.mapName as string) || 'china'
  const measure = data.measures[0] || 'value'
  const mapData = data.dimensions.map((dim, i) => ({
    name: dim,
    value: data.series[0]?.data[i] as number,
  }))

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    visualMap: {
      min: 0,
      max: Math.max(...mapData.map((d) => d.value || 0), 100),
      left: 'left',
      top: 'bottom',
      text: ['高', '低'],
      calculable: true,
    },
    series: [
      {
        type: 'map' as const,
        map: mapName,
        roam: true,
        label: { show: true, fontSize: 10 },
        data: mapData,
        emphasis: {
          label: { show: true },
          itemStyle: { areaColor: '#cce8ff' },
        },
      },
    ],
  }
}
