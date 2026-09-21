import type { App, Directive, DirectiveBinding } from 'vue'

/**
 * v-permission 指令
 * 用法: v-permission="'sys:user:add'"
 *   或: v-permission="['sys:user:add', 'sys:user:edit']" (任意一个)
 */
export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const permissions: string[] = getPermissionsFromStore()
    if (!value || value.length === 0) return
    const required = Array.isArray(value) ? value : [value]
    const hasPermission = required.some((code) => permissions.includes(code) || permissions.includes('*'))
    if (!hasPermission && el.parentNode) {
      el.parentNode.removeChild(el)
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    const { value, oldValue } = binding
    if (JSON.stringify(value) === JSON.stringify(oldValue)) return
    // 触发重新检查
    const event = new CustomEvent('permission-update')
    el.dispatchEvent(event)
  },
}

function getPermissionsFromStore(): string[] {
  try {
    const raw = window.localStorage.getItem('dataviz_permissions')
    if (!raw) return []
    return JSON.parse(raw) as string[]
  } catch {
    return []
  }
}

/**
 * 注册全局 v-permission 指令
 */
export function setupPermissionDirective(app: App): void {
  app.directive('permission', permissionDirective)
}
