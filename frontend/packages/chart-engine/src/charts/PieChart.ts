import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'

export interface PieDataInput {
  series: Array<{ name: string; value: number }>
}

/**
 * PieChart - 饼图选项生成器
 */
export class PieChart {
  static buildOption(config: ChartConfig, data: PieDataInput): EChartsOption {
    const isRose = !!config.options?.roseType
    const isDoughnut = !!config.options?.doughnut

    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 'left' },
      series: [
        {
          type: 'pie' as const,
          radius: isDoughnut ? ['40%', '70%'] : '70%',
          roseType: isRose ? 'radius' : undefined,
          center: ['50%', '55%'],
          data: data.series,
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)',
            },
          },
          label: { show: true, formatter: '{b}: {d}%' },
        },
      ],
    }
  }
}
