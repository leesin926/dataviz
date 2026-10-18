/**
 * 数据源类型
 *
 * 取值必须落在后端 DatasourceType 枚举的 code 上（fromCode 用 equalsIgnoreCase 匹配，未知 code 直接抛
 * IllegalArgumentException 落到兜底 handler 变成 500）。后端没有 hive/spark/restapi，
 * REST 接口类型在枚举里叫 api。
 */
export type DatasourceType =
  | 'mysql'
  | 'postgresql'
  | 'oracle'
  | 'sqlserver'
  | 'clickhouse'
  | 'elasticsearch'
  | 'mongodb'
  | 'api'
  | 'csv'
  | 'excel'

export interface Datasource {
  id: string | number
  name: string
  type: DatasourceType
  description?: string
  /** 连接配置。后端出入参与落库都是 JSON 文本列，序列化由 api-client 收口 */
  config: DatasourceConfig
  /** 后端 status 列是 1=启用 / 0=禁用，这里给语义值 */
  status: 'active' | 'inactive'
  createdAt?: string
  updatedAt?: string
}

/** 只列 DataSourceConnectionFactory 真正会读的键，多余的键传了也不生效 */
export interface DatasourceConfig {
  host?: string
  port?: number
  database?: string
  username?: string
  password?: string
  maxPoolSize?: number
  minIdle?: number
}

export interface DatasourceTable {
  name: string
  comment?: string
  tableType?: string
}

export interface DatasourceColumn {
  name: string
  type: string
  length?: number
  nullable?: boolean
  defaultValue?: string
  comment?: string
  primaryKey?: boolean
}
