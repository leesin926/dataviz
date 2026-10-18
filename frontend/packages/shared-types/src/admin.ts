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

/**
 * 许可证信息（对应 LicenseController /info，R 包装后的 data）。
 * <p>{@code activated} 与 {@code valid} 不是一回事：库里有证但证过期时
 * activated=true、valid=false。面板要能分开显示这两种，否则"换过钥匙"和"钥匙还能用"会被混成一件事。</p>
 */
export interface LicenseInfo {
  activated?: boolean
  valid?: boolean
  type?: string
  issuedTo?: string
  issuedAt?: string
  expiresAt?: string
  maxUsers?: number
  maxNodes?: number
  /** 距到期天数；已过期为负数，未激活为 null */
  daysLeft?: number | null
  message?: string
  /** 密钥指纹（sha256 前 6 字节 hex），后端不返回密钥原文 */
  fingerprint?: string
  activateTime?: string
  activatedBy?: string
  [key: string]: unknown
}
