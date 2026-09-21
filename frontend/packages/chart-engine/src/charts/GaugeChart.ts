import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'

export interface GaugeDataInput {
  name: string
  value: number
  max?: number
}

/**
 * GaugeChart - 仪表盘选项生成器
 */
export class GaugeChart {
  static buildOption(config: ChartConfig, data: GaugeDataInput): EChartsOption {
    const max = data.max || (config.options?.max as number) || 100
    const splitNumber = (config.options?.splitNumber as number) || 10

    return {
      tooltip: {
        formatter: '{a} <br/>{b}: {c}%',
      },
      series: [
        {
          type: 'gauge' as const,
          max,
          splitNumber,
          radius: '90%',
          axisLine: {
            lineStyle: {
              width: 10,
              color: [
                [0.3, '#67e0e3'],
                [0.7, '#37a2da'],
                [1, '#fd666d'],
              ],
            },
          },
          pointer: {
            itemStyle: { color: 'auto' },
          },
          axisTick: {
            distance: -10,
            length: 6,
            lineStyle: { color: '#fff', width: 1 },
          },
          splitLine: {
            distance: -12,
            length: 12,
            lineStyle: { color: '#fff', width: 2 },
          },
          axisLabel: {
            color: 'inherit',
            distance: 20,
            fontSize: 12,
          },
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            color: 'inherit',
            fontSize: 24,
            offsetCenter: [0, '70%'],
          },
          data: [{ value: data.value, name: data.name }],
        },
      ],
    }
  }
}
