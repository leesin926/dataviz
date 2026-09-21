import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

/**
 * 旭日图选项构建
 */
export function buildSunburstOption(config: ChartConfig, data: ChartData): EChartsOption {
  // 简单实现: 按两个维度构建父子层级
  const hasTwoDims = data.dimensions.length >= 2
  const treeMap = new Map<string, Map<string, number>>()
  for (const row of data.raw || []) {
    const parent = String(row[config.dimensions[0]?.field ?? ''] ?? 'unknown')
    const child = hasTwoDims ? String(row[config.dimensions[1]?.field ?? ''] ?? 'unknown') : 'item'
    const value = Number(row[data.measures[0] ?? ''] ?? 0)
    if (!treeMap.has(parent)) treeMap.set(parent, new Map())
    const children = treeMap.get(parent)!
    children.set(child, (children.get(child) || 0) + value)
  }

  const sunburstData = Array.from(treeMap.entries()).map(([parent, children]) => ({
    name: parent,
    children: Array.from(children.entries()).map(([name, value]) => ({ name, value })),
  }))

  return {
    tooltip: { formatter: '{b}: {c}' },
    series: [
      {
        type: 'sunburst' as const,
        data: sunburstData,
        radius: ['15%', '90%'],
        label: { fontSize: 11 },
        emphasis: { focus: 'ancestor' },
      },
    ],
  }
}
