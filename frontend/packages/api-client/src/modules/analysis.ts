import type {
  AnalysisQuery,
  DataPermExplain,
  DataPermRule,
  DataPermRulePayload,
  PageQuery,
  PageResult,
  QueryColumn,
  QueryResult,
  R,
} from '@dataviz/shared-types'
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
  truncated?: boolean
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
    truncated: vo.truncated === true,
  }
}

/** 执行可视化分析查询（裸 SQL 接口是 POST /analysis/query，此处走的是可视化配置引擎） */
export async function executeQuery(query: AnalysisQuery): Promise<QueryResult> {
  const res = await request.post<R<QueryResultVO>>('/analysis/query/execute', toDTO(query))
  return fromVO(res.data.data)
}

/**
 * 导出查询结果。后端把文件流直接写进响应，行数与是否截断放在响应头里
 * （X-Export-Rows / X-Export-Truncated），所以这里不能只回一个 Blob ——
 * "下下来的就是全部数据"是导出功能最容易说谎的地方，截断必须让调用方看得见。
 */
export interface ExportedFile {
  blob: Blob
  fileName: string
  rowCount: number
  /** true = 达到导出上限，文件里不是全量 */
  truncated: boolean
}

export async function exportQueryResult(
  query: AnalysisQuery,
  options: { format?: 'excel' | 'csv'; fileName?: string; allRows?: boolean } = {},
): Promise<ExportedFile> {
  const format = options.format ?? 'excel'
  const res = await request.post(`/analysis/export/${format}`,
    { query: toDTO(query), fileName: options.fileName, allRows: options.allRows === true },
    { responseType: 'blob', timeout: 300000 })
  const blob = res.data as Blob
  // 失败时后端回的是 JSON（权限不足、数据集不存在），blob 里装着一段错误文本而不是文件
  if (blob.type && blob.type.includes('json')) {
    throw new Error(await readBlobErrorMessage(blob))
  }
  const headers = res.headers as Record<string, string>
  return {
    blob,
    fileName: options.fileName || (format === 'csv' ? 'export.csv' : 'export.xlsx'),
    rowCount: Number(headers?.['x-export-rows'] ?? 0),
    truncated: String(headers?.['x-export-truncated']) === 'true',
  }
}

async function readBlobErrorMessage(blob: Blob): Promise<string> {
  try {
    const parsed = JSON.parse(await blob.text()) as { message?: string }
    // 没有 message 时回空串：文案由界面用自己的 locale 兜底，包在这里不硬编码中文
    return parsed.message ?? ''
  } catch {
    return ''
  }
}

/** 行列权限规则分页 */
export async function pagePermRules(query: PageQuery & { keyword?: string }): Promise<PageResult<DataPermRule>> {
  const res = await request.get<R<PageResult<DataPermRule>>>('/analysis/perm-rule/list', { params: query })
  return res.data.data
}

export async function getPermRule(id: string | number): Promise<DataPermRule> {
  const res = await request.get<R<DataPermRule>>(`/analysis/perm-rule/${id}`)
  return res.data.data
}

export async function createPermRule(data: DataPermRulePayload): Promise<string | number> {
  const res = await request.post<R<string | number>>('/analysis/perm-rule', data)
  return res.data.data
}

export async function updatePermRule(id: string | number, data: DataPermRulePayload): Promise<void> {
  await request.put<R<void>>(`/analysis/perm-rule/${id}`, data)
}

export async function deletePermRule(id: string | number): Promise<void> {
  await request.delete<R<void>>(`/analysis/perm-rule/${id}`)
}

/** 当前身份在该数据集上实际命中哪些规则、遮哪些列；datasetId 不传表示只看全局规则 */
export async function explainPermRules(datasetId?: string | number): Promise<DataPermExplain> {
  const res = await request.get<R<DataPermExplain>>('/analysis/perm-rule/explain', { params: { datasetId } })
  return res.data.data
}
