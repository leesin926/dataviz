import type { Datasource, DatasourceTable, TestConnectionResult, PageQuery, PageResult, R } from '@dataviz/shared-types'
import { request } from '../request'
import { asPage } from '../unwrap'

/** 分页查询数据源 */
export async function listDatasources(query?: PageQuery & { type?: string }): Promise<PageResult<Datasource>> {
  const res = await request.get('/datasource/list', { params: query })
  return asPage<Datasource>(res.data)
}

/** 获取数据源详情 */
export async function getDatasource(id: string | number): Promise<Datasource> {
  const res = await request.get<R<Datasource>>(`/datasource/${id}`)
  return res.data.data
}

/** 创建数据源 */
export async function createDatasource(data: Partial<Datasource>): Promise<Datasource> {
  const res = await request.post<R<Datasource>>('/datasource', data)
  return res.data.data
}

/** 更新数据源 */
export async function updateDatasource(id: string | number, data: Partial<Datasource>): Promise<void> {
  await request.put(`/datasource/${id}`, data)
}

/** 删除数据源 */
export async function deleteDatasource(id: string | number): Promise<void> {
  await request.delete(`/datasource/${id}`)
}

/** 测试连接 */
export async function testConnection(config: Partial<Datasource>): Promise<TestConnectionResult> {
  const res = await request.post<R<TestConnectionResult>>('/datasource/test', config)
  return res.data.data
}

/** 获取数据库表列表 */
export async function listTables(datasourceId: string | number): Promise<DatasourceTable[]> {
  const res = await request.get<R<DatasourceTable[]>>(`/datasource/${datasourceId}/tables`)
  return res.data.data
}

/** 获取表元数据 (字段) */
export async function getTableMetadata(datasourceId: string | number, tableName: string): Promise<DatasourceTable> {
  const res = await request.get<R<DatasourceTable>>(`/datasource/${datasourceId}/tables/${tableName}`)
  return res.data.data
}

/** 预览表数据 */
export async function previewTableData(
  datasourceId: string | number,
  tableName: string,
  limit = 100,
): Promise<{ columns: string[]; rows: Record<string, unknown>[] }> {
  const res = await request.get<R<{ columns: string[]; rows: Record<string, unknown>[] }>>(
    `/datasource/${datasourceId}/tables/${tableName}/preview`,
    { params: { limit } },
  )
  return res.data.data
}
