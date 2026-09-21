import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 热力图选项构建
 */
export function buildHeatmapOption(config: ChartConfig, data: ChartData): EChartsOption {
  const xData = data.dimensions
  const yData = Array.from(new Set((data.raw || []).map((r) => String(r[data.measures[0]] ?? ''))))
  const heatData: number[][] = []
  let maxVal = 0
  for (const row of data.raw || []) {
    const x = xData.indexOf(String(row[data.measures[0]] ?? ''))
    const y = yData.indexOf(String(row[data.measures[1]] ?? ''))
    const v = Number(row[data.measures[2]] ?? 0)
    if (x >= 0 && y >= 0) {
      heatData.push([x, y, v])
      if (v > maxVal) maxVal = v
    }
  }

  return {
    tooltip: { position: 'top' },
    grid: { left: 60, right: 20, top: 30, bottom: 60 },
    xAxis: { type: 'category', data: xData, splitArea: { show: true } },
    yAxis: { type: 'category', data: yData, splitArea: { show: true } },
    visualMap: { min: 0, max: maxVal || 100, calculable: true, orient: 'horizontal', left: 'center', bottom: 0 },
    series: [
      {
        type: 'heatmap' as const,
        data: heatData,
        label: { show: true },
        emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } },
      },
    ],
  }
}
