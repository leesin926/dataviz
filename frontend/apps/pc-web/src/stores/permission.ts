import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { Permission } from '@dataviz/shared-types'

/** 必须留在 store 之外：递归的 RouteRecordRaw 联合类型放进 setup 返回值里会让 TS 无法推断 usePermissionStore（TS7056/TS2742） */
export function filterRoutesByPermission(rawRoutes: RouteRecordRaw[], perms: string[]): RouteRecordRaw[] {
  const result: RouteRecordRaw[] = []
  for (const route of rawRoutes) {
    const perm = route.meta?.permission as string | undefined
    if (perm && !perms.includes(perm) && !perms.includes('*')) continue
    if (route.children) {
      const copy: RouteRecordRaw = { ...route, children: filterRoutesByPermission(route.children, perms) }
      result.push(copy)
    } else {
      result.push(route)
    }
  }
  return result
}

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
