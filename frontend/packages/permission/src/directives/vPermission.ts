import type { App, Directive, DirectiveBinding } from 'vue'
import { PermissionManager } from '../core/PermissionManager'

/**
 * 全局权限管理器实例（供指令使用）
 */
let globalPermissionManager: PermissionManager | null = null

/**
 * 设置全局权限管理器
 */
export function setGlobalPermissionManager(manager: PermissionManager): void {
  globalPermissionManager = manager
}

/**
 * 设置权限指令到 Vue 应用
 */
export function setupPermissionDirective(app: App): void {
  app.directive('permission', vPermission)
}

/**
 * v-permission 指令
 * 用法：
 *   v-permission="'system:user:add'"           - 检查单个权限
 *   v-permission="['system:user:add', 'system:user:edit']" - 检查任意一个权限
 *   v-permission:all="['system:user:add']"     - 检查所有权限
 *   v-permission:role="'admin'"                - 检查角色
 *   v-permission:roles="['admin', 'editor']"   - 检查任意角色
 */
export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding): void {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding): void {
    checkPermission(el, binding)
  },
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding): void {
  if (!globalPermissionManager) {
    console.warn('[v-permission] PermissionManager not initialized')
    return
  }

  const { value, arg, modifiers } = binding

  if (!value) return

  // 角色检查
  if (arg === 'role') {
    const hasRole = globalPermissionManager.hasRole(value as string)
    if (!hasRole) {
      removeElement(el)
    }
    return
  }

  if (arg === 'roles') {
    const hasRole = globalPermissionManager.hasAnyRole(value as string[])
    if (!hasRole) {
      removeElement(el)
    }
    return
  }

  // 权限检查
  const permissions = Array.isArray(value) ? value : [value]

  let hasPermission: boolean
  if (arg === 'all' || modifiers.all) {
    hasPermission = globalPermissionManager.hasAllPermissions(permissions)
  } else {
    hasPermission = globalPermissionManager.hasAnyPermission(permissions)
  }

  if (!hasPermission) {
    removeElement(el)
  }
}

function removeElement(el: HTMLElement): void {
  // 使用注释节点替换，保留位置信息
  const comment = document.createComment(`v-permission denied`)
  el.parentNode?.replaceChild(comment, el)
}
