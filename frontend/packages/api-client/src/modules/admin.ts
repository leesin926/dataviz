import type {
  R,
  PageQuery,
  PageResult,
  AuditLog,
  TenantRecord,
  SysConfig,
  LicenseInfo,
} from '@dataviz/shared-types'
import axios from 'axios'
import { BASE_URL, request } from '../request'

/**
 * admin-service 接口封装。
 * 网关路由 /api/admin/** 不做 StripPrefix，控制器自带 /api/admin 前缀，
 * 因此这里的相对路径必须是 /admin/xxx（axios baseURL 已含 /api）。
 */

/** 审计日志分页 */
export async function pageAuditLogs(
  query: PageQuery & { username?: string; module?: string; action?: string },
): Promise<PageResult<AuditLog>> {
  const res = await request.get<R<PageResult<AuditLog>>>('/admin/audit/page', { params: query })
  return res.data.data
}

/** 审计日志详情 */
export async function getAuditLog(id: string | number): Promise<AuditLog> {
  const res = await request.get<R<AuditLog>>(`/admin/audit/${id}`)
  return res.data.data
}

/** 导出审计日志（后端直接写 Excel 流） */
export async function exportAuditLogs(params: {
  username?: string
  module?: string
  action?: string
}): Promise<Blob> {
  const res = await request.get('/admin/audit/export', { params, responseType: 'blob' })
  return res.data as Blob
}

/** 租户分页 */
export async function pageTenants(
  query: PageQuery & { keyword?: string; status?: string },
): Promise<PageResult<TenantRecord>> {
  const res = await request.get<R<PageResult<TenantRecord>>>('/admin/tenant/page', { params: query })
  return res.data.data
}

export async function getTenant(id: string | number): Promise<TenantRecord> {
  const res = await request.get<R<TenantRecord>>(`/admin/tenant/${id}`)
  return res.data.data
}

export async function createTenant(data: Partial<TenantRecord>): Promise<string | number> {
  const res = await request.post<R<string | number>>('/admin/tenant', data)
  return res.data.data
}

export async function updateTenant(data: Partial<TenantRecord> & { id: string | number }): Promise<void> {
  await request.put('/admin/tenant', data)
}

export async function deleteTenant(id: string | number): Promise<void> {
  await request.delete(`/admin/tenant/${id}`)
}

export async function setTenantStatus(id: string | number, active: boolean): Promise<void> {
  await request.post(`/admin/tenant/${id}/${active ? 'enable' : 'disable'}`)
}

/** 系统配置分页 */
export async function pageConfigs(
  query: PageQuery & { keyword?: string; configType?: string },
): Promise<PageResult<SysConfig>> {
  const res = await request.get<R<PageResult<SysConfig>>>('/admin/config/page', { params: query })
  return res.data.data
}

export async function createConfig(data: Partial<SysConfig>): Promise<string | number> {
  const res = await request.post<R<string | number>>('/admin/config', data)
  return res.data.data
}

/** 更新配置（后端 ConfigCreateDTO 无 id，按 configKey 定位） */
export async function updateConfig(data: Partial<SysConfig>): Promise<void> {
  await request.put('/admin/config', data)
}

export async function deleteConfig(id: string | number): Promise<void> {
  await request.delete(`/admin/config/${id}`)
}

/** 按 key 读取配置值 */
export async function getConfigByKey(configKey: string): Promise<string> {
  const res = await request.get<R<string>>(`/admin/config/key/${encodeURIComponent(configKey)}`)
  return res.data.data
}

/** 全局哀悼模式的系统配置键（与 admin-service ConfigController 的免登白名单一致） */
export const MOURNING_CONFIG_KEY = 'screen.mourning.enabled'

/**
 * 免登读取公开配置：分享页/匿名端没有 JWT 也要能拿到全局开关，未配置返回 null。
 * 用裸 axios 而非共享实例，避免启动期 401 触发刷新/登出副作用。
 */
export async function getPublicConfig(configKey: string): Promise<string | null> {
  const res = await axios.get<R<string | null>>(`${BASE_URL}/admin/config/public/${encodeURIComponent(configKey)}`)
  return res.data?.data ?? null
}

/** 许可证信息（后端返回裸 Map，无 R 包装） */
export async function getLicenseInfo(): Promise<LicenseInfo> {
  const res = await request.get<LicenseInfo>('/admin/license/info')
  return res.data
}

/** 激活许可证 */
export async function activateLicense(licenseKey: string): Promise<{ success?: boolean; message?: string }> {
  const res = await request.post<{ success?: boolean; message?: string }>('/admin/license/activate', {
    licenseKey,
  })
  return res.data
}

/** 重新校验许可证（后端返回裸 Map） */
export async function validateLicense(): Promise<{ valid?: boolean; message?: string }> {
  const res = await request.get<{ valid?: boolean; message?: string }>('/admin/license/validate')
  return res.data
}
