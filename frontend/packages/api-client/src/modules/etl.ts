import type { EtlDag, EtlNode, EtlRunResult, EtlTask, EtlTaskStatus, PageQuery, PageResult, R } from '@dataviz/shared-types'
import { request } from '../request'
import { asPage, mapPage } from '../unwrap'

/** 后端 EtlTaskVO：DAG 存在 transformConfig(JSON 文本)，status 为 Java 枚举大写名，cron 叫 scheduleCron */
interface EtlTaskVO {
  id: number
  name: string
  description?: string | null
  sourceDatasourceId?: number | null
  targetDatasourceId?: number | null
  sourceTable?: string | null
  targetTable?: string | null
  transformConfig?: string | null
  scheduleCron?: string | null
  status?: string | null
  lastRunTime?: string | null
  lastRunStatus?: string | null
  createTime?: string | null
  updateTime?: string | null
}

interface EtlTaskLogVO {
  id: number
  taskId: number
  startTime?: string | null
  endTime?: string | null
  status?: string | null
  recordsRead?: number | null
  recordsWritten?: number | null
  errorMessage?: string | null
}

const STATUS_BY_CODE: Record<string, EtlTaskStatus> = {
  DRAFT: 'draft',
  RUNNING: 'running',
  SUCCESS: 'success',
  COMPLETED: 'success',
  FAILED: 'failed',
  ERROR: 'failed',
  STOPPED: 'stopped',
  PAUSED: 'stopped',
  SCHEDULED: 'scheduled',
}

const CODE_BY_STATUS: Record<string, string> = {
  draft: 'DRAFT',
  running: 'RUNNING',
  success: 'SUCCESS',
  failed: 'FAILED',
  stopped: 'STOPPED',
  scheduled: 'SCHEDULED',
}

function statusOf(code?: string | null): EtlTaskStatus {
  if (!code) return 'stopped'
  return STATUS_BY_CODE[code.toUpperCase()] ?? 'stopped'
}

function parseDag(raw?: string | null): EtlDag {
  if (!raw) return { nodes: [], edges: [] }
  try {
    const v = JSON.parse(raw) as Partial<EtlDag>
    return { nodes: Array.isArray(v.nodes) ? v.nodes : [], edges: Array.isArray(v.edges) ? v.edges : [] }
  } catch {
    return { nodes: [], edges: [] }
  }
}

function fromVO(row: EtlTaskVO): EtlTask {
  const lastRunResult: EtlRunResult | undefined = row.lastRunStatus
    ? {
        status: statusOf(row.lastRunStatus),
        startedAt: row.lastRunTime ?? '',
        finishedAt: row.lastRunTime ?? undefined,
      }
    : undefined
  return {
    id: row.id,
    name: row.name,
    description: row.description ?? undefined,
    dag: parseDag(row.transformConfig),
    status: statusOf(row.status),
    schedule: row.scheduleCron ?? undefined,
    lastRunAt: row.lastRunTime ?? undefined,
    lastRunResult,
    createdAt: row.createTime ?? undefined,
    updatedAt: row.updateTime ?? undefined,
  }
}

function toPayload(data: Partial<EtlTask>): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  if (data.name !== undefined) payload.name = data.name
  if (data.description !== undefined) payload.description = data.description
  if (data.dag !== undefined) payload.transformConfig = JSON.stringify(data.dag)
  if (data.schedule !== undefined) payload.scheduleCron = data.schedule
  if (data.status !== undefined) payload.status = CODE_BY_STATUS[data.status]
  return payload
}

/** 分页查询 ETL 任务（后端搜索参数是 name，不是 keyword） */
export async function listEtlTasks(query?: PageQuery & { status?: string; keyword?: string }): Promise<PageResult<EtlTask>> {
  const { status, keyword, ...rest } = query ?? {}
  const res = await request.get('/etl/task/list', {
    params: { ...rest, name: keyword, status: status ? (CODE_BY_STATUS[status] ?? status.toUpperCase()) : undefined },
  })
  const page = asPage<EtlTaskVO>(res.data)
  return mapPage(page, fromVO)
}

/** 获取 ETL 任务详情 */
export async function getEtlTask(id: string | number): Promise<EtlTask> {
  const res = await request.get<R<EtlTaskVO>>(`/etl/task/${id}`)
  return fromVO(res.data.data)
}

/** 创建 ETL 任务（后端只回主键） */
export async function createEtlTask(data: Partial<EtlTask>): Promise<EtlTask> {
  const res = await request.post<R<number>>('/etl/task', toPayload(data))
  return { ...(data as EtlTask), id: res.data.data }
}

/** 更新 ETL 任务 */
export async function updateEtlTask(id: string | number, data: Partial<EtlTask>): Promise<void> {
  await request.put(`/etl/task/${id}`, toPayload(data))
}

/** 删除 ETL 任务 */
export async function deleteEtlTask(id: string | number): Promise<void> {
  await request.delete(`/etl/task/${id}`)
}

/** 执行 ETL 任务（后端为 start，无 runId；执行记录通过 getEtlRunLogs 查询） */
export async function executeEtlTask(id: string | number): Promise<void> {
  await request.post(`/etl/task/${id}/start`)
}

/** 停止 ETL 任务 */
export async function stopEtlTask(id: string | number): Promise<void> {
  await request.post(`/etl/task/${id}/stop`)
}

/** 获取 ETL 执行日志（后端返回运行记录对象，转成可读行） */
export async function getEtlRunLogs(taskId: string | number): Promise<string[]> {
  const res = await request.get<R<EtlTaskLogVO[]>>(`/etl/task/${taskId}/logs`)
  return (res.data.data ?? []).map((l) => {
    const parts = [
      `[${l.status ?? '-'}]`,
      l.startTime ?? '',
      l.endTime ? `~ ${l.endTime}` : '',
      l.recordsRead != null ? `读取 ${l.recordsRead} 行` : '',
      l.recordsWritten != null ? `写入 ${l.recordsWritten} 行` : '',
      l.errorMessage ? `错误: ${l.errorMessage}` : '',
    ].filter(Boolean)
    return parts.join(' ')
  })
}

/** 获取可用算子列表 */
export async function listOperators(): Promise<EtlNode[]> {
  const res = await request.get<R<EtlNode[]>>('/etl/operator/list')
  return res.data.data
}
