/**
 * 告警相关类型
 */

export type AlertLevel = 'info' | 'warning' | 'critical'
export type AlertStatus = 'enabled' | 'disabled' | 'firing' | 'resolved'
export type NotifyChannel = 'email' | 'sms' | 'webhook' | 'wechat' | 'dingtalk' | 'feishu'

export interface AlertRule {
  id: string | number
  name: string
  description?: string
  level: AlertLevel
  status: AlertStatus
  datasetId?: string | number
  metric?: string
  condition: AlertCondition
  notify: AlertNotify
  silenceMinutes?: number
  lastFiredAt?: string
  createdBy?: string | number
  createdAt?: string
  updatedAt?: string
}

export interface AlertCondition {
  type: 'threshold' | 'rate' | 'trend'
  operator: '>' | '>=' | '<' | '<=' | '=' | '!='
  value: number
  durationSeconds?: number // 持续 n 秒
}

export interface AlertNotify {
  channels: NotifyChannel[]
  /**
   * 这条规则挂的<b>通知组 id</b>（收件人从这里来，不再来自渠道配置）。
   * 原先这里是 `receivers`（用户 ID / webhook URL），但那一路从来没有真解析过任何人：
   * 派发时读的是渠道 config 里的一串固定收件人，所以"改收件人"必须去改渠道，
   * 而同一条渠道可以给不同规则发不同的人才是能动态调整的形态。
   */
  groupIds: Array<string | number>
  /** 后端的组摘要（含每组可达地址数），列表/弹窗直接显示用 */
  groups?: NotifyGroupOption[]
  /** 挂了组但组里没有一条这个规则用得上的地址：界面要把这件事显形 */
  targetsEmpty?: boolean
}

export interface AlertEvent {
  id: string | number
  ruleId: string | number
  ruleName: string
  level: AlertLevel
  status: AlertStatus
  message: string
  value?: number
  firedAt: string
  resolvedAt?: string
  notified?: boolean
  acknowledged?: boolean
}

/**
 * 通知渠道配置（notify_channel 表）。
 *
 * 类型码是 `string` 而不是联合类型：渠道清单由后端 `/alert/channel/schema` 导出，
 * 后端加一种渠道时前端不该需要改类型定义。原先那个小写 `NotifyChannel` 联合是**规则里勾选的渠道名**，
 * 两者口径不同（那里存大写类型码，页面渲染前转小写），不要合并。
 */
export type ChannelFieldKind = 'text' | 'password' | 'number' | 'switch' | 'list' | 'select' | 'json' | 'textarea'

/** 一个配置项的自述，配置页据此渲染控件 */
export interface ChannelField {
  key: string
  kind: ChannelFieldKind | string
  required?: boolean
  /** 读取时后端只回 `***`；提交 `***` 或留空表示沿用库里那份 */
  secret?: boolean
  url?: boolean
  labelKey: string
  placeholder?: string
  defaultValue?: string
  options?: string[]
}

export interface ChannelSchema {
  type: string
  /** 该渠道是否已接入真实发送（当前只有 SMS 为 false：配置能存能校验，发送必然失败） */
  deliverable: boolean
  /**
   * 这条渠道需要哪一类收件人：`email` 收邮箱地址、`mobile` 收手机号、`none` 根本没有收件人概念
   * （Webhook、不 @ 人的机器人）。测试发送弹窗据此决定是否显示临时收件人输入。
   */
  targetKind: NotifyTargetKind | string
  fields: ChannelField[]
}

/** 渠道要的收件人类别，与后端 `AlertNotifier.targetKind()` 同名同值 */
export type NotifyTargetKind = 'email' | 'mobile' | 'none'

/**
 * 渠道配置里历史上放收件人的那几个键。收件人已改到「通知对象」，但后端为了升级当天不断发
 * 仍然保留这些存量值，配置页要能「看见」它们而不是隐形带着。
 */
export const LEGACY_RECIPIENT_KEYS = ['to', 'receivers', 'mobiles', 'atMobiles']

export function legacyRecipientKeys(config: Record<string, unknown> | undefined): string[] {
  if (!config) return []
  return LEGACY_RECIPIENT_KEYS.filter((key) => {
    const value = config[key]
    return !(value === null || value === undefined || value === '' || (Array.isArray(value) && !value.length))
  })
}

export interface NotifyChannelConfig {
  id: string | number
  name: string
  type: string
  enabled: boolean
  config: Record<string, unknown>
  deliverable: boolean
  /** 同类型还有更靠前的启用记录 ⇒ 这一条永远不会被派发用到 */
  shadowedByOtherRow?: boolean
  shadowId?: string | number
  createTime?: string
  updateTime?: string
}

export interface NotifyChannelPayload {
  id?: string | number
  name: string
  type: string
  enabled?: boolean
  config: Record<string, unknown>
}

/** 「测试发送」的结果：失败信息作为数据回，不是一条 500 */
export interface ChannelTestResult {
  success: boolean
  channel: string
  recipient?: string
  error?: string
  elapsedMs: number
  deliverable: boolean
  /**
   * 这次实际用的收件人是从哪来的：`GROUP`（规则挂的通知组）/ `CHANNEL_CONFIG`
   * （渠道里存的历史收件人）/ `ADHOC`（弹窗里临时填的）/ `NONE`（一个都没解析出来）。
   * 收件人挪到规则侧以后，"发到的是谁"必须有来源可说，否则测试通过也证明不了实发给对的人。
   */
  recipientSource?: string
}

// ---------------- 通知对象（收件人）：联系人 + 通知组 ----------------

/** 一个可达的人（alert_contact）。邮箱与手机至少有一个，后端在服务层判 */
export interface AlertContact {
  id: string | number
  name: string
  email?: string
  mobile?: string
  remark?: string
  enabled: boolean
  /** 被几个通知组含着：删除守卫会点名这些组，列表里先让人看见 */
  groupCount: number
  createTime?: string
  updateTime?: string
}

/** 留空即清空（后端可空列走显式 SET，不会把 null 当"不改"） */
export interface AlertContactPayload {
  id?: string | number
  name: string
  email?: string
  mobile?: string
  remark?: string
  enabled?: boolean
}

export interface AlertNotifyGroup {
  id: string | number
  name: string
  description?: string
  enabled: boolean
  /** 组内成员（含停用的，界面要说得清） */
  members: AlertContact[]
  /** 被几条规则引用 */
  ruleCount: number
  createTime?: string
  updateTime?: string
}

export interface AlertNotifyGroupPayload {
  id?: string | number
  name: string
  description?: string
  enabled?: boolean
  /** `undefined` = 不改成员，`[]` = 清空成员 */
  contactIds?: Array<string | number>
}

/** 规则表单/规则列表用的组摘要：计数就是为了当场判断"这组收得到这条渠道吗" */
export interface NotifyGroupOption {
  id: string | number
  name: string
  enabled: boolean
  memberCount: number
  emailCount: number
  mobileCount: number
}

