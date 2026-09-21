import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'

export interface MapDataInput {
  mapName: string
  data: Array<{ name: string; value: number }>
}

/**
 * MapChart - 地图选项生成器
 */
export class MapChart {
  static buildOption(config: ChartConfig, data: MapDataInput): EChartsOption {
    const max = Math.max(...data.data.map((d) => d.value), 1)

    return {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c}',
      },
      visualMap: {
        min: 0,
        max,
        left: 'left',
        top: 'bottom',
        text: ['高', '低'],
        calculable: true,
        inRange: {
          color: ['#e0f3f8', '#ffffbf', '#fee090', '#fdae61', '#f46d43', '#d73027'],
        },
      },
      series: [
        {
          type: 'map' as const,
          map: data.mapName,
          roam: config.options?.roam !== false,
          label: { show: true },
          emphasis: {
            label: { show: true },
            itemStyle: { areaColor: '#ffd479' },
          },
          data: data.data,
        },
      ],
    }
  }
}
