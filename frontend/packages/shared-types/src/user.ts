/**
 * 用户相关类型
 */

export type UserStatus = 0 | 1 // 0=禁用, 1=启用

export interface User {
  id: string | number
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  deptId?: string | number
  deptName?: string
  status: UserStatus
  tenantId?: string | number
  roles?: Role[]
  roleIds?: Array<string | number>
  permissions?: string[]
  createdAt?: string
  loginTime?: string
  updatedAt?: string
  lastLoginAt?: string
}

export interface LoginForm {
  username: string
  password: string
  captchaCode?: string
  captchaKey?: string
  tenantCode?: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  roles: string[]
  permissions: string[]
}

export interface UserInfo {
  id: string | number
  username: string
  nickname: string
  avatar: string
  email: string
  phone: string
  dept: Dept | null
  roles: Role[]
  permissions: string[]
  isAdmin: boolean
}

export interface Role {
  id: string | number
  tenantId?: string | number
  roleCode: string
  roleName: string
  description?: string
  sortOrder?: number
  status: UserStatus
  dataScope?: string
  createdAt?: string
}

export interface Permission {
  id: string | number
  parentId?: string | number
  type: 'menu' | 'button' | 'api'
  name: string
  code: string
  path?: string
  icon?: string
  sort?: number
  visible?: boolean
  children?: Permission[]
}

/** 菜单权限树节点，字段对齐 user-service PermissionTreeVO */
export interface PermissionNode {
  id: string | number
  parentId?: string | number
  permissionCode: string
  permissionName: string
  /** 1=目录 2=菜单 3=按钮 */
  type: number
  path?: string
  icon?: string
  sortOrder?: number
  status?: number
  children?: PermissionNode[]
}

/** 部门树节点，字段对齐 user-service DeptTreeVO */
export interface Dept {
  id: string | number
  tenantId?: string | number
  parentId?: string | number
  deptName: string
  leader?: string
  phone?: string
  sortOrder?: number
  status?: number
  children?: Dept[]
}

export interface Tenant {
  id: string | number
  code: string
  name: string
  contact?: string
  phone?: string
  status: UserStatus
  expireAt?: string
  maxUsers?: number
  createdAt?: string
}
