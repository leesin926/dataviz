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
  receivers: Array<string | number> // 用户 ID / webhook URL
  template?: string
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
