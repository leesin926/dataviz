/**
 * 告警接口（对齐 alert-service /api/alert/event/*）
 */

import { request } from './request'

export interface AlertEvent {
  id: number
  ruleId: number
  ruleName: string
  triggerValue?: number
  severity: string
  status: string
  message?: string
  notifiedAt?: string
  resolvedAt?: string
  createTime: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export function listEvents(params: {
  pageNum: number
  pageSize: number
  status?: string
  severity?: string
  ruleId?: number
}): Promise<PageResult<AlertEvent>> {
  return request<PageResult<AlertEvent>>('GET', '/alert/event/page', params)
}

export function acknowledgeEvent(id: number, note?: string): Promise<void> {
  return request<void>('POST', '/alert/event/acknowledge', { id, note })
}

export function resolveEvent(id: number, note?: string): Promise<void> {
  return request<void>('POST', '/alert/event/resolve', { id, note })
}
