import type { Dashboard, PageQuery, PageResult } from '@dataviz/shared-types'
import { request } from '../request'
import { asPage, unwrap } from '../unwrap'

/** 仪表盘分页列表 */
export async function listDashboards(query?: PageQuery & { keyword?: string; status?: string }): Promise<PageResult<Dashboard>> {
  const res = await request.get('/dashboard/page', { params: query })
  return asPage<Dashboard>(res.data)
}

/** 获取仪表盘详情 */
export async function getDashboard(id: string | number): Promise<Dashboard> {
  const res = await request.get(`/dashboard/${id}`)
  return unwrap<Dashboard>(res.data)
}

/** 创建仪表盘 */
export async function createDashboard(data: Partial<Dashboard>): Promise<string | number> {
  const res = await request.post('/dashboard', data)
  return unwrap<string | number>(res.data)
}

/** 更新仪表盘 */
export async function updateDashboard(id: string | number, data: Partial<Dashboard>): Promise<void> {
  await request.put('/dashboard', { ...data, id })
}

/** 删除仪表盘 */
export async function deleteDashboard(id: string | number): Promise<void> {
  await request.delete(`/dashboard/${id}`)
}

/** 发布仪表盘 */
export async function publishDashboard(id: string | number): Promise<void> {
  await request.post(`/dashboard/${id}/publish`)
}

/** 取消发布 */
export async function unpublishDashboard(id: string | number): Promise<void> {
  await request.post(`/dashboard/${id}/unpublish`)
}

/** 克隆仪表盘（后端 /copy 只按 id 复制，名称固定追加 " (Copy)"，不接受自定义名称） */
export async function cloneDashboard(id: string | number): Promise<Dashboard> {
  const res = await request.post(`/dashboard/${id}/copy`)
  return unwrap<Dashboard>(res.data)
}
