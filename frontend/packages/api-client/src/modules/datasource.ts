import type {
  Datasource,
  DatasourceColumn,
  DatasourceConfig,
  DatasourceTable,
  DatasourceType,
  PageQuery,
  PageResult,
  R,
} from '@dataviz/shared-types'
import { request } from '../request'
import { asPage, mapPage } from '../unwrap'

/**
 * datasource 域的出入参与前端类型不同形，在这里收口：
 * - config：DatasourceCreateDTO/UpdateDTO/VO 都是 String（JSON 文本列），传对象会被 Jackson 判为
 *   类型不匹配直接 400
 * - status：后端是 Integer（1=启用 0=禁用），不是字符串枚举
 * - 时间列：后端叫 createTime/updateTime
 * - 列表 VO（DatasourceListVO）不含 config，所以地址列在列表上取不到，见报告 API-107
 */
interface DatasourceVO {
  id: number
  name: string
  type: string
  config?: string | null
  status?: number | null
  description?: string | null
  createTime?: string
  updateTime?: string
}

interface TableInfoVO {
  tableName: string
  tableComment?: string | null
  tableType?: string | null
}

interface ColumnInfoVO {
  columnName: string
  dataType: string
  columnSize?: number | null
  nullable?: boolean | null
  defaultValue?: string | null
  comment?: string | null
  primaryKey?: boolean | null
}

function parseConfig(raw?: string | null): DatasourceConfig {
  if (!raw) return {}
  try {
    const parsed: unknown = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as DatasourceConfig) : {}
  } catch {
    return {}
  }
}

function fromVO(row: DatasourceVO): Datasource {
  return {
    id: row.id,
    name: row.name,
    type: row.type as DatasourceType,
    description: row.description ?? undefined,
    config: parseConfig(row.config),
    status: row.status === 1 ? 'active' : 'inactive',
    createdAt: row.createTime,
    updatedAt: row.updateTime,
  }
}

/** status 不传：DatasourceUpdateDTO 会被 BeanUtils 整份拷进实体，带上默认值等于替后端改状态 */
function toPayload(data: Partial<Datasource>): Record<string, unknown> {
  return {
    name: data.name ?? null,
    type: data.type ?? null,
    description: data.description ?? null,
    config: JSON.stringify(data.config ?? {}),
  }
}

/** 分页查询数据源（不含连接配置） */
export async function listDatasources(
  query?: PageQuery & { name?: string; type?: string; status?: number },
): Promise<PageResult<Datasource>> {
  const res = await request.get('/datasource/list', { params: query })
  return mapPage(asPage<DatasourceVO>(res.data), fromVO)
}

/** 获取数据源详情（含 config 原文，凭据按后端现有行为原样返回） */
export async function getDatasource(id: string | number): Promise<Datasource> {
  const res = await request.get<R<DatasourceVO>>(`/datasource/${id}`)
  return fromVO(res.data.data)
}

/** 创建数据源。JDBC 类型会在服务端先连一次，连不上就不落库；返回新 id */
export async function createDatasource(data: Partial<Datasource>): Promise<string | number> {
  const res = await request.post<R<number>>('/datasource', toPayload(data))
  return res.data.data
}

/** 更新数据源 */
export async function updateDatasource(id: string | number, data: Partial<Datasource>): Promise<void> {
  await request.put(`/datasource/${id}`, toPayload(data))
}

/** 删除数据源 */
export async function deleteDatasource(id: string | number): Promise<void> {
  await request.delete(`/datasource/${id}`)
}

/**
 * 测试"表单里还没落库"的连接。
 *
 * 失败有两条路：服务端取不到连接时抛 BizException（信封 code≠200，响应拦截器已转成 Error，
 * message 就是 JDBC 原话）；取到连接但 isValid(5) 为 false 时才是 data=false。
 * 这里把后者收敛成 false，前者留给调用方 catch。
 */
export async function testConnection(data: Partial<Datasource>): Promise<boolean> {
  const res = await request.post<R<boolean>>('/datasource/test-connection', {
    type: data.type ?? null,
    config: JSON.stringify(data.config ?? {}),
  })
  return res.data.data === true
}

/** 测试"列表里某一行"的连接：由服务端按 id 读库中配置，前端不必也不该回传口令 */
export async function testExistingConnection(id: string | number): Promise<boolean> {
  const res = await request.post<R<boolean>>(`/datasource/${id}/test-connection`)
  return res.data.data === true
}

/** 获取数据库表列表（非 JDBC 数据源服务端会明确报错，不会给空列表） */
export async function listTables(datasourceId: string | number): Promise<DatasourceTable[]> {
  const res = await request.get<R<TableInfoVO[]>>(`/datasource/${datasourceId}/tables`)
  return res.data.data.map((t) => ({
    name: t.tableName,
    comment: t.tableComment ?? undefined,
    tableType: t.tableType ?? undefined,
  }))
}

/** 获取表的列信息 */
export async function listTableColumns(
  datasourceId: string | number,
  tableName: string,
): Promise<DatasourceColumn[]> {
  const res = await request.get<R<ColumnInfoVO[]>>(
    `/datasource/${datasourceId}/tables/${encodeURIComponent(tableName)}/columns`,
  )
  return res.data.data.map((c) => ({
    name: c.columnName,
    type: c.dataType,
    length: c.columnSize ?? undefined,
    nullable: c.nullable ?? undefined,
    defaultValue: c.defaultValue ?? undefined,
    comment: c.comment ?? undefined,
    primaryKey: c.primaryKey ?? undefined,
  }))
}
