import { computed } from 'vue'
import type { User } from '@dataviz/shared-types'

/**
 * 权限 composable
 */
export function usePermission() {
  const permissions = computed<string[]>(() => {
    try {
      const raw = window.localStorage.getItem('dataviz_permissions')
      if (!raw) return []
      return JSON.parse(raw) as string[]
    } catch {
      return []
    }
  })

  const user = computed<User | null>(() => {
    try {
      const raw = window.localStorage.getItem('dataviz_user')
      if (!raw) return null
      return JSON.parse(raw) as User
    } catch {
      return null
    }
  })

  const isAdmin = computed(() => {
    const u = user.value
    return u?.roles?.some((r) => r.roleKey === 'admin') ?? false
  })

  /**
   * 是否有任意一个权限
   */
  function hasAny(codes: string | string[]): boolean {
    const list = Array.isArray(codes) ? codes : [codes]
    if (isAdmin.value) return true
    return list.some((c) => permissions.value.includes(c))
  }

  /**
   * 是否同时拥有所有权限
   */
  function hasAll(codes: string[]): boolean {
    if (isAdmin.value) return true
    return codes.every((c) => permissions.value.includes(c))
  }

  /**
   * 角色判断
   */
  function hasRole(roleKey: string | string[]): boolean {
    const u = user.value
    if (!u?.roles) return false
    const keys = Array.isArray(roleKey) ? roleKey : [roleKey]
    return u.roles.some((r) => keys.includes(r.roleKey))
  }

  return {
    permissions,
    user,
    isAdmin,
    hasAny,
    hasAll,
    hasRole,
  }
}
