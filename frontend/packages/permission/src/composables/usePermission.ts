import { computed, type Ref, ref } from 'vue'
import { PermissionManager } from '../core/PermissionManager'
import type { Role } from '@dataviz/shared-types'

/**
 * usePermission - 权限 composable
 * 提供响应式的权限检查方法
 */
export function usePermission(permissionManager: PermissionManager) {
  // 内部响应式引用，用于触发更新
  const _version = ref(0)

  /**
   * 检查是否拥有指定权限
   */
  function hasPermission(permission: string): boolean {
    _version.value // 建立响应式依赖
    return permissionManager.hasPermission(permission)
  }

  /**
   * 检查是否拥有任意一个权限
   */
  function hasAnyPermission(permissions: string[]): boolean {
    _version.value
    return permissionManager.hasAnyPermission(permissions)
  }

  /**
   * 检查是否拥有所有权限
   */
  function hasAllPermissions(permissions: string[]): boolean {
    _version.value
    return permissionManager.hasAllPermissions(permissions)
  }

  /**
   * 检查是否拥有指定角色
   */
  function hasRole(roleKey: string): boolean {
    _version.value
    return permissionManager.hasRole(roleKey)
  }

  /**
   * 检查是否拥有任意角色
   */
  function hasAnyRole(roleKeys: string[]): boolean {
    _version.value
    return permissionManager.hasAnyRole(roleKeys)
  }

  /**
   * 是否是管理员
   */
  const isAdmin = computed(() => {
    _version.value
    return permissionManager.getIsAdmin()
  })

  /**
   * 获取所有权限
   */
  const permissions = computed(() => {
    _version.value
    return permissionManager.getPermissions()
  })

  /**
   * 获取所有角色
   */
  const roles = computed(() => {
    _version.value
    return permissionManager.getRoles()
  })

  /**
   * 触发更新（权限数据变化后调用）
   */
  function refresh(): void {
    _version.value++
  }

  /**
   * 设置权限（快捷方法）
   */
  function setPermissions(perms: string[]): void {
    permissionManager.setPermissions(perms)
    refresh()
  }

  /**
   * 设置角色（快捷方法）
   */
  function setRoles(newRoles: Role[]): void {
    permissionManager.setRoles(newRoles)
    refresh()
  }

  /**
   * 清除权限
   */
  function clear(): void {
    permissionManager.clear()
    refresh()
  }

  return {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    hasAnyRole,
    isAdmin,
    permissions,
    roles,
    refresh,
    setPermissions,
    setRoles,
    clear,
  }
}
