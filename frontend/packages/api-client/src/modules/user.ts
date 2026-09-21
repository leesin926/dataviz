import type { Dept, PageQuery, PageResult, PermissionNode, R, Role, User } from '@dataviz/shared-types'
import { request } from '../request'
import { asPage } from '../unwrap'

/**
 * user-service：网关 StripPrefix=1，控制器映射为 /user、/dept、/role、/permission，
 * 因此前端相对路径是 /user/xxx（baseURL /api 会被网关剥掉）。
 * X-Tenant-Id / X-User-Id 由网关在校验 JWT 后注入，前端无需自传。
 */

/** 分页查询用户（后端 UserQueryDTO 仅支持用户名/昵称/状态过滤，租户由网关注入） */
export async function listUsers(
  query: PageQuery & { username?: string; nickname?: string; status?: number },
): Promise<PageResult<User>> {
  const res = await request.get('/user/page', { params: query })
  return asPage<User>(res.data)
}

/** 获取用户详情 */
export async function getUser(id: string | number): Promise<User> {
  const res = await request.get<R<User>>(`/user/${id}`)
  return res.data.data
}

/** 当前登录用户档案（user-service）；会话态信息（角色/权限）用 auth.ts 的 getCurrentUser */
export async function getCurrentUserProfile(): Promise<User> {
  const res = await request.get<R<User>>('/user/current')
  return res.data.data
}

/** 创建用户 */
export async function createUser(data: Partial<User> & { password: string }): Promise<string | number> {
  const res = await request.post<R<string | number>>('/user', data)
  return res.data.data
}

/** 更新用户 */
export async function updateUser(id: string | number, data: Partial<User>): Promise<void> {
  await request.put(`/user/${id}`, data)
}

/** 启用/停用用户 */
export async function setUserStatus(id: string | number, status: number): Promise<void> {
  await request.put(`/user/${id}/status/${status}`)
}

/** 删除用户 */
export async function deleteUser(id: string | number): Promise<void> {
  await request.delete(`/user/${id}`)
}

/** 重置密码 */
export async function resetPassword(id: string | number, newPassword: string): Promise<void> {
  await request.put(`/user/${id}/password/reset`, null, { params: { newPassword } })
}

/** 给用户分配角色 */
export async function assignUserRoles(id: string | number, roleIds: Array<string | number>): Promise<void> {
  await request.post(`/user/${id}/roles`, roleIds)
}

/** 获取部门树 */
export async function getDeptTree(): Promise<Dept[]> {
  const res = await request.get<R<Dept[]>>('/dept/tree')
  return res.data.data
}

/** 创建部门 */
export async function createDept(data: Partial<Dept>): Promise<string | number> {
  const res = await request.post<R<string | number>>('/dept', data)
  return res.data.data
}

/** 更新部门 */
export async function updateDept(id: string | number, data: Partial<Dept>): Promise<void> {
  await request.put(`/dept/${id}`, data)
}

/** 删除部门 */
export async function deleteDept(id: string | number): Promise<void> {
  await request.delete(`/dept/${id}`)
}

/** 角色列表（后端返回全量数组，这里包成统一的分页结构） */
export async function listRoles(): Promise<PageResult<Role>> {
  const res = await request.get('/role/list')
  return asPage<Role>(res.data)
}

/** 创建角色 */
export async function createRole(data: Partial<Role>): Promise<string | number> {
  const res = await request.post<R<string | number>>('/role', data)
  return res.data.data
}

/** 更新角色 */
export async function updateRole(id: string | number, data: Partial<Role>): Promise<void> {
  await request.put(`/role/${id}`, data)
}

/** 删除角色 */
export async function deleteRole(id: string | number): Promise<void> {
  await request.delete(`/role/${id}`)
}

/** 角色授权 */
export async function assignRolePermissions(id: string | number, permissionIds: Array<string | number>): Promise<void> {
  await request.post(`/role/${id}/permissions`, permissionIds)
}

/** 菜单权限树 */
export async function getPermissionTree(): Promise<PermissionNode[]> {
  const res = await request.get<R<PermissionNode[]>>('/permission/tree')
  return res.data.data ?? []
}

/** 新增菜单权限 */
export async function createPermission(data: Partial<PermissionNode>): Promise<string | number> {
  const res = await request.post<R<string | number>>('/permission', data)
  return res.data.data
}

/** 更新菜单权限 */
export async function updatePermission(id: string | number, data: Partial<PermissionNode>): Promise<void> {
  await request.put(`/permission/${id}`, data)
}

/** 删除菜单权限 */
export async function deletePermission(id: string | number): Promise<void> {
  await request.delete(`/permission/${id}`)
}

