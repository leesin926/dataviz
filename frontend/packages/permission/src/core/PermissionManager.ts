import type { Role, Permission } from '@dataviz/shared-types'

/**
 * PermissionManager - 权限管理器
 * 基于 RBAC 模型进行权限检查和角色管理
 */
export class PermissionManager {
  private permissions: Set<string> = new Set()
  private roles: Role[] = []
  private isAdmin = false

  /**
   * 设置用户权限
   */
  setPermissions(permissions: string[]): void {
    this.permissions = new Set(permissions)
  }

  /**
   * 设置用户角色
   */
  setRoles(roles: Role[]): void {
    this.roles = roles
    // 检查是否是超级管理员
    this.isAdmin = roles.some((r) => r.roleCode === 'admin' || r.roleCode === 'super_admin')
  }

  /**
   * 设置管理员状态
   */
  setAdmin(isAdmin: boolean): void {
    this.isAdmin = isAdmin
  }

  /**
   * 检查是否拥有指定权限
   */
  hasPermission(permission: string): boolean {
    if (this.isAdmin) return true
    return this.permissions.has(permission)
  }

  /**
   * 检查是否拥有任意一个权限
   */
  hasAnyPermission(permissions: string[]): boolean {
    if (this.isAdmin) return true
    return permissions.some((p) => this.permissions.has(p))
  }

  /**
   * 检查是否拥有所有权限
   */
  hasAllPermissions(permissions: string[]): boolean {
    if (this.isAdmin) return true
    return permissions.every((p) => this.permissions.has(p))
  }

  /**
   * 检查是否拥有指定角色
   */
  hasRole(roleCode: string): boolean {
    if (this.isAdmin) return true
    return this.roles.some((r) => r.roleCode === roleCode)
  }

  /**
   * 检查是否拥有任意一个角色
   */
  hasAnyRole(roleCodes: string[]): boolean {
    if (this.isAdmin) return true
    return roleCodes.some((key) => this.roles.some((r) => r.roleCode === key))
  }

  /**
   * 是否是管理员
   */
  getIsAdmin(): boolean {
    return this.isAdmin
  }

  /**
   * 获取所有权限
   */
  getPermissions(): string[] {
    return Array.from(this.permissions)
  }

  /**
   * 获取所有角色
   */
  getRoles(): Role[] {
    return [...this.roles]
  }

  /**
   * 清除权限数据
   */
  clear(): void {
    this.permissions.clear()
    this.roles = []
    this.isAdmin = false
  }

  /**
   * 从权限树中提取权限码
   */
  static extractPermissionCodes(permissions: Permission[]): string[] {
    const codes: string[] = []

    function traverse(nodes: Permission[]): void {
      for (const node of nodes) {
        if (node.code) {
          codes.push(node.code)
        }
        if (node.children && node.children.length > 0) {
          traverse(node.children)
        }
      }
    }

    traverse(permissions)
    return codes
  }

  /**
   * 检查权限码是否匹配（支持通配符）
   */
  static matchPermission(required: string, actual: Set<string>): boolean {
    // 精确匹配
    if (actual.has(required)) return true

    // 通配符匹配：如 'system:*' 匹配 'system:user:list'
    for (const perm of actual) {
      if (perm.endsWith(':*')) {
        const prefix = perm.slice(0, -2)
        if (required.startsWith(prefix)) return true
      }
      if (perm === '*') return true
    }

    return false
  }
}
