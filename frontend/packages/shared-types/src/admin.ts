/** 管理端（admin-service）相关类型，字段与后端 VO 一一对应 */

/** 审计日志（对应 admin-service AuditLogVO） */
export interface AuditLog {
  id: string | number
  userId?: string | number
  username?: string
  module?: string
  action?: string
  method?: string
  requestUrl?: string
  requestParams?: string
  responseCode?: number
  ip?: string
  userAgent?: string
  /** 执行耗时（毫秒） */
  executionTime?: number
  tenantId?: string | number
  createTime?: string
}

/** 租户（对应 TenantVO） */
export interface TenantRecord {
  id: string | number
  name: string
  code: string
  /** ACTIVE / DISABLED */
  status: 'ACTIVE' | 'DISABLED' | string
  contactName?: string
  contactPhone?: string
  expireTime?: string
  maxUsers?: number
  config?: string
  createTime?: string
  updateTime?: string
}

/** 系统配置（对应 ConfigVO） */
export interface SysConfig {
  id: string | number
  configKey: string
  configValue?: string
  /** SYSTEM / CUSTOM */
  configType?: string
  remark?: string
  tenantId?: string | number
  createTime?: string
  updateTime?: string
}

/** 许可证信息（对应 LicenseController /info，后端直接返回 Map，无 R 包装） */
export interface LicenseInfo {
  type?: string
  issuedTo?: string
  issuedAt?: string
  expiresAt?: string
  maxUsers?: number
  maxNodes?: number
  valid?: boolean
  [key: string]: unknown
}
