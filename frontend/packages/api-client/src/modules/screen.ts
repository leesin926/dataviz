import type { Screen, ScreenPlatform, ScreenVariant, PageQuery, PageResult, R } from '@dataviz/shared-types'
import { normalizeScreenInk } from '@dataviz/shared-types'
import { request } from '../request'
import { asPage } from '../unwrap'

/** 大屏分页列表；传 platform 时 width/height 按该端返回（该端未单独配置则回退 pc 尺寸） */
export async function listScreens(
  query?: PageQuery & { keyword?: string; status?: string; platform?: ScreenPlatform },
): Promise<PageResult<Screen>> {
  const res = await request.get('/screen/list', { params: query })
  return asPage<Screen>(res.data)
}

/** 获取大屏详情；uni 端播放器用 getScreenForPlatform */
export async function getScreen(id: string | number): Promise<Screen> {
  const res = await request.get<R<Screen>>(`/screen/${id}`)
  return normalizeScreenInk(res.data.data)
}

/** 获取指定端变体展平后的大屏配置（该端缺省时后端回退 pc 顶层配置） */
export async function getScreenForPlatform(id: string | number, platform: ScreenPlatform): Promise<Screen> {
  const res = await request.get<R<Screen>>(`/screen/${id}`, { params: { platform } })
  return normalizeScreenInk(res.data.data)
}

/** 按端保存配置变体 */
export async function saveScreenVariant(id: string | number, platform: ScreenPlatform, variant: ScreenVariant): Promise<void> {
  await request.put(`/screen/${id}/variant`, variant, { params: { platform } })
}

/** 创建大屏 */
export async function createScreen(data: Partial<Screen>): Promise<Screen> {
  const res = await request.post<R<Screen>>('/screen', data)
  return res.data.data
}

/** 更新大屏 */
export async function updateScreen(id: string | number, data: Partial<Screen>): Promise<void> {
  await request.put(`/screen/${id}`, data)
}

/** 删除大屏 */
export async function deleteScreen(id: string | number): Promise<void> {
  await request.delete(`/screen/${id}`)
}

/** 发布大屏 */
export async function publishScreen(id: string | number): Promise<void> {
  await request.post(`/screen/${id}/publish`)
}

/** 取消发布 */
export async function unpublishScreen(id: string | number): Promise<void> {
  await request.post(`/screen/${id}/unpublish`)
}

/** 克隆大屏 */
export async function cloneScreen(id: string | number, newName?: string): Promise<Screen> {
  const res = await request.post<R<Screen>>(`/screen/${id}/clone`, { name: newName })
  return normalizeScreenInk(res.data.data)
}

/** 取回分享标识（后端幂等：已生成则原样返回，链接稳定） */
export async function shareScreen(id: string | number): Promise<string> {
  const res = await request.post<R<string>>(`/screen/${id}/share`)
  return res.data.data
}

/** 免登录读取已发布大屏（分享/嵌入用，走网关白名单） */
export async function getSharedScreen(token: string, platform?: ScreenPlatform): Promise<Screen> {
  const res = await request.get<R<Screen>>(`/screen/share/${token}`, { params: platform ? { platform } : {} })
  return normalizeScreenInk(res.data.data)
}
