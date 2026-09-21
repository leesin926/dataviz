/**
 * 数据源类型
 */

export type DatasourceType =
  | 'mysql'
  | 'postgresql'
  | 'oracle'
  | 'sqlserver'
  | 'clickhouse'
  | 'hive'
  | 'spark'
  | 'elasticsearch'
  | 'mongodb'
  | 'restapi'
  | 'csv'
  | 'excel'

export interface Datasource {
  id: string | number
  name: string
  type: DatasourceType
  description?: string
  config: DatasourceConfig
  status: 'active' | 'inactive' | 'error'
  lastTestAt?: string
  lastTestResult?: string
  createdBy?: string | number
  createdAt?: string
  updatedAt?: string
}

export interface DatasourceConfig {
  host?: string
  port?: number
  database?: string
  username?: string
  password?: string
  schema?: string
  url?: string
  options?: Record<string, unknown>
}

export interface DatasourceTable {
  name: string
  schema?: string
  comment?: string
  columns: DatasourceColumn[]
}

export interface DatasourceColumn {
  name: string
  type: string
  comment?: string
  nullable?: boolean
  primaryKey?: boolean
  length?: number
  precision?: number
  scale?: number
}

export interface TestConnectionResult {
  success: boolean
  message?: string
  latency?: number // ms
}
