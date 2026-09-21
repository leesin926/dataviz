import type { AlertRule, AlertEvent, AlertCondition, NotifyChannel, PageQuery, PageResult, R } from '@dataviz/shared-types'
import { request } from '../request'
import { asPage, mapPage } from '../unwrap'

/** 后端 AlertRuleVO（severity/type/condition 为大写枚举、enabled 为布尔）与前端 AlertRule 的双向映射 */
const OPERATOR_BY_CODE: Record<string, AlertCondition['operator']> = {
  GT: '>',
  GTE: '>=',
  LT: '<',
  LTE: '<=',
  EQ: '=',
  NE: '!=',
}
const CODE_BY_OPERATOR: Record<string, string> = {
  '>': 'GT',
  '>=': 'GTE',
  '<': 'LT',
  '<=': 'LTE',
  '=': 'EQ',
  '!=': 'NE',
}
const CONDITION_TYPE_BY_RULE_TYPE: Record<string, AlertCondition['type']> = {
  THRESHOLD: 'threshold',
  DERIVATIVE: 'rate',
  COMPOSITE: 'trend',
}

function fromVO(row: Record<string, unknown>): AlertRule {
  const code = typeof row.condition === 'string' ? row.condition.toUpperCase() : ''
  const severity = typeof row.severity === 'string' ? row.severity.toLowerCase() : 'info'
  const notifyChannels = Array.isArray(row.notifyChannels) ? (row.notifyChannels as string[]) : []
  return {
    ...(row as unknown as AlertRule),
    level: severity === 'critical' ? 'critical' : severity === 'warning' ? 'warning' : 'info',
    status: row.enabled === false || row.enabled === 0 ? 'disabled' : 'enabled',
    metric: (row.metricExpression as string) ?? row.metric,
    condition: {
      type: CONDITION_TYPE_BY_RULE_TYPE[String(row.type ?? '').toUpperCase()] ?? 'threshold',
      operator: OPERATOR_BY_CODE[code] ?? '>',
      value: Number(row.threshold ?? 0),
      durationSeconds: row.duration == null ? undefined : Number(row.duration),
    },
    notify: {
      channels: notifyChannels.map((c) => c.toLowerCase() as NotifyChannel),
      receivers: [],
    },
    lastFiredAt: (row.lastFiredAt as string) ?? undefined,
    createdAt: (row.createTime as string) ?? row.createdAt,
    updatedAt: (row.updateTime as string) ?? row.updatedAt,
  }
}

function toPayload(data: Partial<AlertRule>): Record<string, unknown> {
  const condition = data.condition
  return {
    name: data.name,
    description: data.description,
    type: condition?.type === 'rate' ? 'DERIVATIVE' : condition?.type === 'trend' ? 'COMPOSITE' : 'THRESHOLD',
    datasourceId: data.datasetId,
    metricExpression: data.metric,
    condition: CODE_BY_OPERATOR[condition?.operator ?? '>'] ?? 'GT',
    threshold: condition?.value,
    duration: condition?.durationSeconds,
    severity: (data.level ?? 'warning').toUpperCase(),
    notifyChannels: (data.notify?.channels ?? []).map((c) => c.toUpperCase()),
  }
}

/** 告警规则列表 */
export async function listAlertRules(query?: PageQuery & { level?: string; status?: string; keyword?: string }): Promise<PageResult<AlertRule>> {
  const { level, status, ...rest } = query ?? {}
  const res = await request.get('/alert/rule/page', {
    params: { ...rest, severity: level?.toUpperCase() },
  })
  return mapPage(asPage<Record<string, unknown>>(res.data), fromVO)
}

/** 创建告警规则 */
export async function createAlertRule(data: Partial<AlertRule>): Promise<AlertRule> {
  const res = await request.post<R<Record<string, unknown>>>('/alert/rule', toPayload(data))
  return fromVO(res.data.data)
}

/** 更新告警规则（后端按请求体中的 id 定位，路径不含 id） */
export async function updateAlertRule(id: string | number, data: Partial<AlertRule>): Promise<void> {
  await request.put('/alert/rule', { ...toPayload(data), id: Number(id) })
}

/** 删除告警规则 */
export async function deleteAlertRule(id: string | number): Promise<void> {
  await request.delete(`/alert/rule/${id}`)
}

/** 启用/禁用规则（后端为 /enable 与 /disable 两个动作） */
export async function toggleAlertRule(id: string | number, enabled: boolean): Promise<void> {
  await request.post(`/alert/rule/${id}/${enabled ? 'enable' : 'disable'}`)
}

/** 试跑规则：后端只取数并判定条件，不会生成事件、不会发通知 */
export interface AlertRuleTestResult {
  value: number | null
  triggered: boolean
  note: string | null
}

export async function testAlertRule(id: string | number): Promise<AlertRuleTestResult> {
  const res = await request.post<R<AlertRuleTestResult>>(`/alert/rule/${id}/test`)
  return res.data.data
}

/** 告警事件列表 */
export async function listAlertEvents(query?: PageQuery & { ruleId?: string | number; level?: string; status?: string }): Promise<PageResult<AlertEvent>> {
  const { level, ...rest } = query ?? {}
  const res = await request.get('/alert/event/page', { params: { ...rest, severity: level?.toUpperCase() } })
  const page = asPage<Record<string, unknown>>(res.data)
  const map = (row: Record<string, unknown>): AlertEvent => ({
    ...(row as unknown as AlertEvent),
    level: String(row.severity ?? 'info').toLowerCase() as AlertEvent['level'],
    status: String(row.status ?? 'PENDING').toLowerCase() as AlertEvent['status'],
    value: row.triggerValue == null ? undefined : Number(row.triggerValue),
    firedAt: (row.createTime as string) ?? row.firedAt,
    acknowledged: row.status !== 'PENDING',
    notified: Boolean(row.notifiedAt),
  })
  return mapPage(page, map)
}

/** 确认告警事件 */
export async function acknowledgeAlertEvent(id: string | number, note?: string): Promise<void> {
  await request.post('/alert/event/acknowledge', { id: Number(id), note })
}

/** 解决告警事件 */
export async function resolveAlertEvent(id: string | number, note?: string): Promise<void> {
  await request.post('/alert/event/resolve', { id: Number(id), note })
}

/** 告警统计 */
export async function getAlertStats(): Promise<Record<string, number>> {
  const res = await request.get<R<Record<string, number>>>('/alert/event/stats')
  return res.data.data
}
