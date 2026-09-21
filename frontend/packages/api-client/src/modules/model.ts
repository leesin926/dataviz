import type { QueryDataset, QueryDimension, QueryMeasure, R, PageQuery, PageResult } from '@dataviz/shared-types'
import { request } from '../request'
import { mapPage, asPage } from '../unwrap'

/**
 * 后端 DatasetVO（model-service）：SQL 列名为 sqlQuery，dimensions/metrics/filters 均为 JSON 文本列
 * （ModelDataset 实体这三个字段就是 String），前端 QueryDataset 需要数组，故在此做双向转换。
 */
interface DatasetVO {
  id: number
  name: string
  datasourceId: number
  tableName?: string
  sqlQuery?: string
  dimensions?: string | null
  metrics?: string | null
  filters?: string | null
  tenantId?: string
  createTime?: string
  updateTime?: string
}

function parseJsonArray<T>(raw: string | null | undefined): T[] {
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as T[]) : []
  } catch {
    return []
  }
}

function fromVO(row: DatasetVO): QueryDataset {
  return {
    id: row.id,
    name: row.name,
    datasourceId: row.datasourceId,
    tableName: row.tableName ?? undefined,
    sql: row.sqlQuery ?? undefined,
    dimensions: parseJsonArray<QueryDimension>(row.dimensions),
    measures: parseJsonArray<QueryMeasure>(row.metrics),
  }
}

/** DatasetCreateDTO 的三个 JSON 列必须传字符串，传数组会被 Jackson 判为类型不匹配直接 400 */
function toPayload(data: Partial<QueryDataset>): Record<string, unknown> {
  return {
    name: data.name,
    datasourceId: data.datasourceId,
    tableName: data.tableName ?? null,
    sqlQuery: data.sql ?? null,
    dimensions: JSON.stringify(data.dimensions ?? []),
    metrics: JSON.stringify(data.measures ?? []),
  }
}

/** 数据集列表（X-Tenant-Id 由网关按 JWT 注入，前端无需传） */
export async function listDatasets(query?: PageQuery & { keyword?: string }): Promise<PageResult<QueryDataset>> {
  const res = await request.get('/model/dataset/list', { params: query })
  return mapPage(asPage<DatasetVO>(res.data), fromVO)
}

/** 创建数据集（后端只返回新 id，其余字段回填请求内容） */
export async function createDataset(data: Partial<QueryDataset>): Promise<QueryDataset> {
  const res = await request.post<R<number>>('/model/dataset', toPayload(data))
  return {
    id: res.data.data,
    name: data.name ?? '',
    datasourceId: data.datasourceId ?? 0,
    tableName: data.tableName,
    sql: data.sql,
    dimensions: data.dimensions ?? [],
    measures: data.measures ?? [],
  }
}

/** 获取数据集详情 */
export async function getDataset(id: string | number): Promise<QueryDataset> {
  const res = await request.get<R<DatasetVO>>(`/model/dataset/${id}`)
  return fromVO(res.data.data)
}

/** 更新数据集 */
export async function updateDataset(id: string | number, data: Partial<QueryDataset>): Promise<void> {
  await request.put(`/model/dataset/${id}`, toPayload(data))
}

/** 删除数据集 */
export async function deleteDataset(id: string | number): Promise<void> {
  await request.delete(`/model/dataset/${id}`)
}
