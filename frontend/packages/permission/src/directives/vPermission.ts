import type { App, Directive, DirectiveBinding } from 'vue'
import { matchesPermissionCode } from '../core/matchesPermissionCode'
import { readSessionPermissions } from '../core/session'

/**
 * v-permission —— 按钮级权限的唯一一份指令实现。
 *
 * 用法：
 *   v-permission="'system:user:add'"                     单个权限码
 *   v-permission="['system:user:add', 'system:user:edit']" 任一命中即显示
 *   v-permission.all="['a:write','b:write']"             需要同时具备
 *
 * 两条口径写死在这里，不再各处另写一份：
 * 1. 匹配语义 = `matchesPermissionCode`（精确 / `*` / `xxx:*` 前缀通），与后端 `PermissionInterceptor.matches`
 *    逐条等价。用裸 `includes` 的那份实现会让被授予 `system:*` 的角色"接口通、按钮不见了"。
 * 2. 权限码来源 = {@link readSessionPermissions}，即登录时落盘的那份信封；app 若把权限放在别处，
 *    用 `setupPermissionDirective(app, source)` 注入，不要去改指令内部。
 *
 * ⚠️ 指令只做"看不见/点不到"，从来不是安全边界 —— 闸门在后端注解上。反过来说，正因为不是安全边界，
 * 这里宁可保守：判不出来就隐藏按钮，后端那侧该给的 403 一样会给。
 */
export type PermissionCodesSource = () => string[]

let codesSource: PermissionCodesSource = readSessionPermissions

/** 覆盖权限码来源（在 `setupPermissionDirective` 之后单独调用时，需已挂载的组件重渲染才会生效） */
export function setPermissionCodesSource(source: PermissionCodesSource): void {
  codesSource = source
}

export function setupPermissionDirective(app: App, source?: PermissionCodesSource): void {
  if (source) {
    codesSource = source
  }
  app.directive('permission', vPermission)
}

interface HiddenSlot {
  parent: Node
  anchor: Comment
  next: Node | null
}

const hiddenSlots = new WeakMap<HTMLElement, HiddenSlot>()

export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding): void {
    applyPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding): void {
    applyPermission(el, binding)
  },
}

function applyPermission(el: HTMLElement, binding: DirectiveBinding): void {
  const required = toCodeList(binding.value)
  // 没写码位等于"不要求权限"，与路由守卫对空 required 的处理一致
  if (required.length === 0) {
    restoreElement(el)
    return
  }
  const needAll = binding.arg === 'all' || binding.modifiers.all === true
  if (isAllowed(required, codesSource() || [], needAll)) {
    restoreElement(el)
  } else {
    hideElement(el)
  }
}

/**
 * 与 `v-permission` 同一判据、同一数据源的函数版本 —— 给"从 DOM 里摘掉不合适"的控件用：
 * 开关、输入框这类还承载着只读信息的元素，该 `:disabled="!hasSessionPermission(...)"` 而不是让用户看不见状态。
 */
export function hasSessionPermission(codes: string | string[], needAll = false): boolean {
  return isAllowed(toCodeList(codes), codesSource() || [], needAll)
}

function isAllowed(required: string[], held: string[], needAll: boolean): boolean {
  if (required.length === 0) {
    return true
  }
  return needAll
    ? required.every((code) => matchesPermissionCode(code, held))
    : required.some((code) => matchesPermissionCode(code, held))
}

function toCodeList(value: unknown): string[] {
  if (!value) {
    return []
  }
  const list = Array.isArray(value) ? value : [value]
  const codes: string[] = []
  for (const item of list) {
    if (typeof item === 'string' && item) {
      codes.push(item)
    }
  }
  return codes
}

/** 摘掉元素但留一个注释锚点，好让权限变化时还能放回原位（原实现只有"摘"，摘完就再也回不来） */
function hideElement(el: HTMLElement): void {
  if (hiddenSlots.has(el)) {
    return
  }
  const parent = el.parentNode
  if (!parent) {
    return
  }
  const next = el.nextSibling
  const anchor = document.createComment('v-permission:hidden')
  parent.replaceChild(anchor, el)
  hiddenSlots.set(el, { parent, anchor, next })
}

function restoreElement(el: HTMLElement): void {
  const slot = hiddenSlots.get(el)
  if (!slot) {
    return
  }
  hiddenSlots.delete(el)
  slot.parent.removeChild(slot.anchor)
  if (slot.next && slot.next.parentNode === slot.parent) {
    slot.parent.insertBefore(el, slot.next)
  } else {
    slot.parent.appendChild(el)
  }
}
