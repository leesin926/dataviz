/**
 * 查询相关类型
 */

import type { AggregationType, ChartFilter } from './chart'

export interface AnalysisQuery {
  datasetId: string | number
  dimensions: Array<{
    field: string
    alias?: string
  }>
  measures: Array<{
    field: string
    alias?: string
    aggregation: AggregationType
  }>
  filters?: ChartFilter[]
  sorts?: Array<{ field: string; order: 'asc' | 'desc' }>
  limit?: number
  offset?: number
}

export interface QueryResult {
  columns: QueryColumn[]
  rows: Record<string, unknown>[]
  sql?: string // 执行的 SQL
  elapsed: number // ms
  total?: number
}

export interface QueryColumn {
  field: string
  type: 'string' | 'number' | 'date' | 'boolean'
  displayName?: string
  format?: string
}

export interface QueryMetric {
  field: string
  aggregation: AggregationType
}

export interface QueryDataset {
  id: string | number
  name: string
  datasourceId: string | number
  tableName?: string
  sql?: string
  dimensions: QueryDimension[]
  measures: QueryMeasure[]
  parameters?: QueryParameter[]
}

export interface QueryDimension {
  field: string
  displayName: string
  type: 'category' | 'time'
  format?: string
}

export interface QueryMeasure {
  field: string
  displayName: string
  aggregation?: AggregationType
  format?: string
}

export interface QueryParameter {
  name: string
  type: 'string' | 'number' | 'date'
  defaultValue?: unknown
}
