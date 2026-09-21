import type { AnalysisQuery, ChartConfig } from '@dataviz/shared-types'

/**
 * 解析可视化配置为 AnalysisQuery
 */
export class QueryParser {
  /**
   * 从 ChartConfig 解析查询
   */
  parse(config: unknown): AnalysisQuery {
    const c = config as Partial<ChartConfig>
    return {
      datasetId: c.datasetId || 0,
      dimensions: (c.dimensions || []).map((d) => ({
        field: d.field,
        alias: d.alias || d.field,
      })),
      measures: (c.measures || []).map((m) => ({
        field: m.field,
        alias: m.alias || m.field,
        aggregation: m.aggregation || 'sum',
      })),
      filters: c.filters || [],
      sorts: c.sorts || [],
      limit: 1000,
    }
  }

  /**
   * 从 SQL 字符串解析简单查询 (示例实现)
   */
  parseSql(sql: string): Partial<AnalysisQuery> {
    // 简单示例: 解析 SELECT 字段, FROM 表
    const selectMatch = /SELECT\s+(.+?)\s+FROM/i.exec(sql)
    const fields = selectMatch ? selectMatch[1].split(',').map((f) => f.trim()) : []
    return {
      dimensions: fields.map((f) => ({ field: f, alias: f })),
      measures: [],
    }
  }
}
