import type {
  AlertRule,
  AlertEvent,
  AlertCondition,
  NotifyChannel,
  ChannelSchema,
  NotifyChannelConfig,
  NotifyChannelPayload,
  ChannelTestResult,
  AlertContact,
  AlertContactPayload,
  AlertNotifyGroup,
  AlertNotifyGroupPayload,
  NotifyGroupOption,
  PageQuery,
  PageResult,
  R,
} from '@dataviz/shared-types'
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
  const groups = Array.isArray(row.notifyGroups) ? (row.notifyGroups as NotifyGroupOption[]) : []
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
      groupIds: groups.map((g) => g.id),
      groups,
      targetsEmpty: row.notifyTargetsEmpty === true,
    },
    lastFiredAt: (row.lastFiredAt as string) ?? undefined,
    createdAt: (row.createTime as string) ?? row.createdAt,
    updatedAt: (row.updateTime as string) ?? row.updatedAt,
  }
}

function toPayload(data: Partial<AlertRule>): Record<string, unknown> {
  const condition = data.condition
  const notify = data.notify
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
    notifyChannels: (notify?.channels ?? []).map((c) => String(c).toUpperCase()),
    //  undefined = 这次不动挂载关系；[] = 明确清空。两者语义不同，不能在这里把 undefined 变成 []
    notifyGroupIds: notify ? notify.groupIds : undefined,
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

/**
 * 创建告警规则。后端回的是新 id（不是整条 VO），所以这里也只能回 id ——
 * 原先写成 `fromVO(res.data.data)`，等于把一条数字 id 当规则对象铺给调用方，界面拿到的是空字段。
 */
export async function createAlertRule(data: Partial<AlertRule>): Promise<string | number> {
  const res = await request.post<R<string | number>>('/alert/rule', toPayload(data))
  return res.data.data
}

/** 规则详情（编辑弹窗按这一条取全量，列表页的行对象可能缺后端后补的字段） */
export async function getAlertRule(id: string | number): Promise<AlertRule> {
  const res = await request.get<R<Record<string, unknown>>>(`/alert/rule/${id}`)
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

// ---------------- 通知渠道配置 ----------------

/** 后端 ChannelField.MASK：读回来的口令是这三字符，原样提交回去表示"不改" */
export const CHANNEL_SECRET_MASK = '***'

/**
 * 渠道表单的字段清单。由后端注册表（有哪些 AlertNotifier）导出，配置页据此渲染 ——
 * 所以"界面上能选到的渠道"恰好等于"服务端有实现的渠道"。
 */
export async function listChannelSchemas(): Promise<ChannelSchema[]> {
  const res = await request.get<R<ChannelSchema[]>>('/alert/channel/schema')
  return res.data.data ?? []
}

export async function pageNotifyChannels(
  query: PageQuery & { keyword?: string; type?: string },
): Promise<PageResult<NotifyChannelConfig>> {
  const res = await request.get<R<unknown>>('/alert/channel/page', { params: query })
  return asPage<NotifyChannelConfig>(res.data)
}

export async function createNotifyChannel(data: NotifyChannelPayload): Promise<string | number> {
  const res = await request.post<R<string | number>>('/alert/channel', data)
  return res.data.data
}

/** 更新渠道（后端按请求体里的 id 定位，路径不含 id，与 /alert/rule 同形） */
export async function updateNotifyChannel(data: NotifyChannelPayload): Promise<void> {
  await request.put('/alert/channel', data)
}

export async function deleteNotifyChannel(id: string | number): Promise<void> {
  await request.delete(`/alert/channel/${id}`)
}

export async function toggleNotifyChannel(id: string | number, enabled: boolean): Promise<void> {
  await request.post(`/alert/channel/${id}/${enabled ? 'enable' : 'disable'}`)
}

/**
 * 测试发送：拿一条合成消息真发一次。
 * 失败也走成功响应（`success=false` + `error` 原话），调用方不该把"我配错了"读成"接口坏了"。
 *
 * `recipients` 是给这一次实测的<b>临时</b>收件人，不落库；留空则走与真实派发完全相同的那条解析路
 * （规则挂的通知组 → 渠道里存的历史收件人），这样"测试通过而实发发错人"不会回来。
 */
export async function testNotifyChannel(id: string | number, recipients?: string): Promise<ChannelTestResult> {
  const res = await request.post<R<ChannelTestResult>>(`/alert/channel/${id}/test`, {
    recipients: recipients?.trim() || undefined,
  })
  return res.data.data
}

// ---------------- 通知对象（联系人 + 通知组） ----------------

export async function pageContacts(query: PageQuery & { keyword?: string }): Promise<PageResult<AlertContact>> {
  const res = await request.get<R<unknown>>('/alert/contact/page', { params: query })
  return asPage<AlertContact>(res.data)
}

/** 通知组成员选择器用的全量联系人（按姓名排序，含停用的） */
export async function listContactOptions(): Promise<AlertContact[]> {
  const res = await request.get<R<AlertContact[]>>('/alert/contact/options')
  return res.data.data ?? []
}

export async function createContact(data: AlertContactPayload): Promise<string | number> {
  const res = await request.post<R<string | number>>('/alert/contact', data)
  return res.data.data
}

/** 更新联系人（后端按请求体里的 id 定位，与 /alert/rule、/alert/channel 同形） */
export async function updateContact(data: AlertContactPayload): Promise<void> {
  await request.put('/alert/contact', data)
}

export async function deleteContact(id: string | number): Promise<void> {
  await request.delete(`/alert/contact/${id}`)
}

export async function toggleContact(id: string | number, enabled: boolean): Promise<void> {
  await request.post(`/alert/contact/${id}/${enabled ? 'enable' : 'disable'}`)
}

export async function pageNotifyGroups(query: PageQuery & { keyword?: string }): Promise<PageResult<AlertNotifyGroup>> {
  const res = await request.get<R<unknown>>('/alert/notify-group/page', { params: query })
  return asPage<AlertNotifyGroup>(res.data)
}

/** 规则表单用的组摘要：带每组"启用且有邮箱/有手机"的人数，勾选当场就能判断收不收得到 */
export async function listNotifyGroupOptions(): Promise<NotifyGroupOption[]> {
  const res = await request.get<R<NotifyGroupOption[]>>('/alert/notify-group/options')
  return res.data.data ?? []
}

export async function getNotifyGroup(id: string | number): Promise<AlertNotifyGroup> {
  const res = await request.get<R<AlertNotifyGroup>>(`/alert/notify-group/${id}`)
  return res.data.data
}

export async function createNotifyGroup(data: AlertNotifyGroupPayload): Promise<string | number> {
  const res = await request.post<R<string | number>>('/alert/notify-group', data)
  return res.data.data
}

export async function updateNotifyGroup(data: AlertNotifyGroupPayload): Promise<void> {
  await request.put('/alert/notify-group', data)
}

export async function deleteNotifyGroup(id: string | number): Promise<void> {
  await request.delete(`/alert/notify-group/${id}`)
}

export async function toggleNotifyGroup(id: string | number, enabled: boolean): Promise<void> {
  await request.post(`/alert/notify-group/${id}/${enabled ? 'enable' : 'disable'}`)
}

