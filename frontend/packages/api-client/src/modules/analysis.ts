import type { AnalysisQuery, QueryColumn, QueryResult, R } from '@dataviz/shared-types'
import { request } from '../request'

/**
 * 后端可视化查询引擎的入参口径（AnalysisQueryDTO）：指标字段叫 metrics/aggFunction，排序叫 orders/direction，
 * 与前端 AnalysisQuery（measures/aggregation、sorts/order）不一致，在此收口转换。
 */
interface AnalysisQueryDTO {
  datasetId: string | number
  dimensions: Array<{ field: string; dateFormat?: string }>
  metrics: Array<{ field: string; aggFunction?: string; alias?: string }>
  filters?: AnalysisQuery['filters']
  orders?: Array<{ field: string; direction: string }>
  limit?: number
}

/** 后端 QueryResultVO：columns 是字符串数组，rows 是列名→值的映射 */
interface QueryResultVO {
  columns?: string[] | null
  rows?: Record<string, unknown>[] | null
  rowCount?: number
  executionTime?: number
  sql?: string
}

function toDTO(query: AnalysisQuery): AnalysisQueryDTO {
  return {
    datasetId: query.datasetId,
    dimensions: (query.dimensions ?? []).map((d) => ({ field: d.field })),
    metrics: (query.measures ?? []).map((m) => ({ field: m.field, aggFunction: m.aggregation, alias: m.alias })),
    filters: query.filters,
    orders: query.sorts?.map((s) => ({ field: s.field, direction: s.order })),
    limit: query.limit,
  }
}

function columnType(sample: unknown): QueryColumn['type'] {
  if (typeof sample === 'number') return 'number'
  if (typeof sample === 'boolean') return 'boolean'
  if (typeof sample === 'string' && /^\d{4}-\d{2}-\d{2}/.test(sample)) return 'date'
  return 'string'
}

function fromVO(vo: QueryResultVO): QueryResult {
  const names = vo.columns ?? []
  const firstRow = vo.rows?.[0]
  return {
    columns: names.map<QueryColumn>((field) => ({
      field,
      type: columnType(firstRow?.[field]),
    })),
    rows: vo.rows ?? [],
    sql: vo.sql,
    elapsed: vo.executionTime ?? 0,
    total: vo.rowCount,
  }
}

/** 执行可视化分析查询（裸 SQL 接口是 POST /analysis/query，此处走的是可视化配置引擎） */
export async function executeQuery(query: AnalysisQuery): Promise<QueryResult> {
  const res = await request.post<R<QueryResultVO>>('/analysis/query/execute', toDTO(query))
  return fromVO(res.data.data)
}
