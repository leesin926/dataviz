import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

interface QueryResultLike {
  columns: Array<{ field: string; type: string }>
  rows: Record<string, unknown>[]
}

/**
 * 将查询结果转换为图表数据格式
 */
export class DataAdapter {
  static transform(config: ChartConfig, result: QueryResultLike): ChartData {
    const rows = result.rows || []
    const dimFields = config.dimensions.map((d) => d.field)
    const measureFields = config.measures.map((m) => m.field)

    // 按维度生成维度值
    const dimensions: string[] = dimFields.length > 0 ? dimFields : ['index']
    const measures: string[] = measureFields

    // 多系列支持: 当存在多个维度时,以最后一个维度作为系列名
    const seriesDim = dimFields.length > 1 ? dimFields[dimFields.length - 1] : null
    const categoryDim = dimFields.length > 0 ? dimFields[0] : null

    const seriesMap = new Map<string, unknown[]>()
    for (const m of measures) {
      seriesMap.set(m, [])
    }

    if (seriesDim && categoryDim) {
      // 透视表模式
      const categoryValues = Array.from(new Set(rows.map((r) => String(r[categoryDim] ?? ''))))
      const seriesValues = Array.from(new Set(rows.map((r) => String(r[seriesDim] ?? ''))))
      for (const sv of seriesValues) {
        for (const m of measures) {
          seriesMap.set(`${m}_${sv}`, [])
        }
      }
      const pivot = new Map<string, Record<string, unknown>>()
      for (const row of rows) {
        const cat = String(row[categoryDim] ?? '')
        const ser = String(row[seriesDim] ?? '')
        const key = `${cat}`
        if (!pivot.has(key)) pivot.set(key, { __category: cat })
        const bucket = pivot.get(key)!
        for (const m of measures) {
          bucket[`${m}_${ser}`] = row[m]
        }
      }
      const series: ChartData['series'] = []
      for (const m of measures) {
        for (const sv of seriesValues) {
          series.push({
            name: `${m} - ${sv}`,
            data: categoryValues.map((cat) => pivot.get(cat)?.[`${m}_${sv}`] ?? null),
          })
        }
      }
      return { dimensions: categoryValues, measures, series, raw: rows }
    }

    // 简单模式
    const series: ChartData['series'] = measures.map((m) => ({
      name: m,
      data: rows.map((r) => r[m] ?? null),
    }))

    const dimValues = categoryDim ? rows.map((r) => String(r[categoryDim] ?? '')) : rows.map((_, i) => String(i + 1))

    return { dimensions: dimValues, measures, series, raw: rows }
  }
}
