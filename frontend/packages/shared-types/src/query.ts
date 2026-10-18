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
  /** true = rows 只是前 N 行，不是全量（后端行数天花板截断，导出全量走 exportAll） */
  truncated?: boolean
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

/**
 * 行列数据权限规则（后端 DataPermRuleVO / DataPermRuleDTO）。
 * <p>行条件存的是结构化三元组而不是管理员手写的一段 SQL —— 渲染时和查询条件走同一份白名单，
 * 所以前端能给出的操作符集合必须与后端 OPERATORS 白名单一致，不能靠自由输入。</p>
 */
export interface DataPermCondition {
  field: string
  operator: string
  /** 标量、数组（in/notin/between），或占位符字符串（:userId 等） */
  value?: unknown
}

export interface DataPermRule {
  id: string | number
  name: string
  /** null / 缺省 = 本租户全部数据集 */
  datasetId?: string | number | null
  scopeType: 'USER' | 'ROLE'
  /** USER 存用户ID，ROLE 存角色码 */
  scopeValue: string
  conditions: DataPermCondition[]
  restrictedColumns: string[]
  /** 后端用 0/1 表达启用状态 */
  enabled?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface DataPermRulePayload {
  name: string
  datasetId?: string | number | null
  scopeType: 'USER' | 'ROLE'
  scopeValue: string
  conditions: DataPermCondition[]
  restrictedColumns: string[]
  enabled: number
  remark?: string
}

/** 「我这个身份在这个数据集上会被怎么限制」的回显（后端 DataPermExplainVO） */
export interface DataPermExplain {
  denyAll: boolean
  rowPredicate: string
  restrictedColumns: string[]
  appliedRuleNames: string[]
  enabledRuleCount: number
}
