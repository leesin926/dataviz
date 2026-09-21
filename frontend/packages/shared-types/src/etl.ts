/**
 * ETL 任务类型
 */

export type EtlTaskStatus = 'draft' | 'running' | 'success' | 'failed' | 'stopped' | 'scheduled'

export type OperatorType =
  | 'input'
  | 'transform'
  | 'output'
  | 'filter'
  | 'join'
  | 'aggregate'
  | 'union'
  | 'sort'
  | 'limit'
  | 'sql'
  | 'script'

export interface EtlTask {
  id: string | number
  name: string
  description?: string
  dag: EtlDag
  status: EtlTaskStatus
  schedule?: string // cron 表达式
  lastRunAt?: string
  lastRunResult?: EtlRunResult
  createdBy?: string | number
  createdAt?: string
  updatedAt?: string
}

/** DAG 定义 */
export interface EtlDag {
  nodes: EtlNode[]
  edges: EtlEdge[]
}

/** DAG 节点 */
export interface EtlNode {
  id: string
  type: OperatorType
  name: string
  x: number
  y: number
  config: EtlNodeConfig
}

/** DAG 边 */
export interface EtlEdge {
  id: string
  source: string
  target: string
  sourcePort?: string
  targetPort?: string
}

/** 节点配置 (根据 type 具体化) */
export interface EtlNodeConfig {
  datasourceId?: string | number
  tableName?: string
  sql?: string
  columns?: string[]
  filters?: Array<{ field: string; operator: string; value: unknown }>
  script?: string
  [key: string]: unknown
}

export interface EtlRunResult {
  status: EtlTaskStatus
  startedAt: string
  finishedAt?: string
  duration?: number
  rowCount?: number
  errorMessage?: string
  logs?: string[]
}
