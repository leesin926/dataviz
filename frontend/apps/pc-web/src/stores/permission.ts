import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { Permission } from '@dataviz/shared-types'

export const usePermissionStore = defineStore('permission', () => {
  const routes = ref<RouteRecordRaw[]>([])
  const addedRoutes = ref<RouteRecordRaw[]>([])
  const permissions = ref<string[]>([])

  const accessibleRoutes = computed(() => routes.value)

  function setRoutes(list: RouteRecordRaw[]) {
    routes.value = list
  }

  function setAddedRoutes(list: RouteRecordRaw[]) {
    addedRoutes.value = list
  }

  function setPermissions(list: string[]) {
    permissions.value = list
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code) || permissions.value.includes('*')
  }

  function filterRoutesByPermission(rawRoutes: RouteRecordRaw[], perms: string[]): RouteRecordRaw[] {
    return rawRoutes
      .filter((r) => {
        const perm = r.meta?.permission as string | undefined
        if (!perm) return true
        return perms.includes(perm) || perms.includes('*')
      })
      .map((r) => ({
        ...r,
        children: r.children ? filterRoutesByPermission(r.children, perms) : undefined,
      }))
  }

  function generateRoutes(menuPermissions: Permission[]): RouteRecordRaw[] {
    const flatCodes = flattenPermissions(menuPermissions)
    setPermissions(flatCodes)
    return flatCodes as unknown as RouteRecordRaw[]
  }

  function flattenPermissions(perms: Permission[]): string[] {
    const result: string[] = []
    for (const p of perms) {
      result.push(p.code)
      if (p.children) result.push(...flattenPermissions(p.children))
    }
    return result
  }

  function reset() {
    routes.value = []
    addedRoutes.value = []
    permissions.value = []
  }

  return {
    routes,
    addedRoutes,
    permissions,
    accessibleRoutes,
    setRoutes,
    setAddedRoutes,
    setPermissions,
    hasPermission,
    filterRoutesByPermission,
    generateRoutes,
    reset,
  }
})
