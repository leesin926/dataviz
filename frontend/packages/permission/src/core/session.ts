/**
 * 会话数据（令牌 / 权限码）的唯一读法 —— 必须与 `shared-utils` 的 `setLocal` 信封格式对齐。
 *
 * 为什么要单独立一个文件：各 app 是用 `setLocal('permissions', [...])` 落盘的，落到 localStorage 上的
 * 真实值是 `{"value":[...],"expireAt":...}` 这层信封。曾经有一处直接把信封 `JSON.parse(...) as string[]`
 * 当数组用，`includes` 当场抛 TypeError —— 类型断言把这个问题从编译期完全藏掉了。读法散在守卫、指令、
 * 各 app 里时，症状是"同一份权限，菜单看得见但按钮点不动"，很难怀疑到解析格式上。
 */

const TOKEN_KEY = 'dataviz_access_token'
const PERMISSIONS_KEY = 'dataviz_permissions'

/** 兼容 shared-utils setLocal 的 {value, expireAt} 信封格式与裸值格式；过期即当作不存在 */
export function readStoredValue(key: string): unknown {
  try {
    const raw = window.localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && 'value' in parsed) {
      const item = parsed as { value: unknown; expireAt?: number }
      if (item.expireAt && Date.now() > item.expireAt) {
        window.localStorage.removeItem(key)
        return null
      }
      return item.value
    }
    return parsed
  } catch {
    return null
  }
}

export function readSessionToken(): string | null {
  const value = readStoredValue(TOKEN_KEY)
  return typeof value === 'string' && value ? value : null
}

export function readSessionPermissions(): string[] {
  const value = readStoredValue(PERMISSIONS_KEY)
  return Array.isArray(value) ? (value as string[]) : []
}
