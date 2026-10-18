import type { ChartConfig, ChartMeasure } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'

interface QueryResultLike {
  columns: Array<{ field: string; type: string }>
  rows: Record<string, unknown>[]
}

/** 与后端 SqlBuilder.aliasColumn 的清洗规则保持一致：非法字符丢掉，全被丢光则回落到 <聚合>_<字段> */
function sanitizeIdentifier(value: string): string {
  return value.replace(/[^a-zA-Z0-9_]/g, '')
}

/**
 * SqlBuilder 给每个聚合列都起别名（`SUM(amount) AS amount`，没给别名时回落 `sum_amount`），
 * 于是结果行的键不等于 chartConfig.measures[].field —— 直接按 field 取值会拿到 undefined，
 * 绑数据集的图表整条系列为空、画出来是一张空图。按后端会产出的几种键名依次回落。
 */
function measureKey(row: Record<string, unknown>, m: ChartMeasure): string {
  const agg = sanitizeIdentifier((m.aggregation ?? 'sum').toLowerCase())
  const field = sanitizeIdentifier(m.field)
  const candidates = [m.field, m.alias ?? '', m.alias ? sanitizeIdentifier(m.alias) : '', `${agg}_${field}`]
  for (const key of candidates) {
    if (key && key in row) return key
  }
  return m.field
}

/**
 * 将查询结果转换为图表数据格式
 */
export class DataAdapter {
  static transform(config: ChartConfig, result: QueryResultLike): ChartData {
    const rows = result.rows || []
    const dimFields = config.dimensions.map((d) => d.field)
    const measureFields = config.measures.map((m) => m.field)
    /** 图例/系列名优先用别名，物理列名（amount）对用户没有可读性 */
    const seriesNames = config.measures.map((m) => m.alias || m.field)

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
        config.measures.forEach((m, i) => {
          bucket[`${measures[i]}_${ser}`] = row[measureKey(row, m)]
        })
      }
      const series: ChartData['series'] = []
      config.measures.forEach((_, i) => {
        for (const sv of seriesValues) {
          series.push({
            name: `${seriesNames[i]} - ${sv}`,
            data: categoryValues.map((cat) => pivot.get(cat)?.[`${measures[i]}_${sv}`] ?? null),
          })
        }
      })
      return { dimensions: categoryValues, measures, series, raw: rows }
    }

    // 简单模式
    const series: ChartData['series'] = config.measures.map((m, i) => ({
      name: seriesNames[i],
      data: rows.map((r) => r[measureKey(r, m)] ?? null),
    }))

    const dimValues = categoryDim ? rows.map((r) => String(r[categoryDim] ?? '')) : rows.map((_, i) => String(i + 1))

    return { dimensions: dimValues, measures, series, raw: rows }
  }
}
