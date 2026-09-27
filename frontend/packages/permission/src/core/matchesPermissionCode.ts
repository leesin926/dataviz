/**
 * 权限码匹配的唯一口径 —— 与后端 `PermissionInterceptor.matches` 必须逐条等价（三份实现，一处改齐）。
 *
 * 为什么要单独立一个函数而不是各写各的：三处调用点（路由守卫、`PermissionManager` 实例方法、
 * 后端拦截器）语义一旦分叉，症状是"菜单看得见点进去 403"或反过来的"接口能调菜单里没有"，
 * 而这两种现象都不会指向"匹配算法不同"，排错成本极高。
 */

const ALL = '*'
const PREFIX_WILDCARD_SUFFIX = ':*'

/** 用户持有的权限码里是否满足 `required`：精确、`*` 全通、`xxx:*` 前缀通 */
export function matchesPermissionCode(required: string, held: Iterable<string>): boolean {
  if (!required) {
    return false
  }
  for (const code of held) {
    if (!code) {
      continue
    }
    if (code === required) {
      return true
    }
    if (code === ALL) {
      return true
    }
    if (code.endsWith(PREFIX_WILDCARD_SUFFIX) && required.startsWith(code.slice(0, -PREFIX_WILDCARD_SUFFIX.length))) {
      return true
    }
  }
  return false
}

/** 任一命中即通过（`required` 为空数组按"不要求权限"处理，与守卫的既有行为一致） */
export function matchesAnyPermissionCode(required: string[], held: Iterable<string>): boolean {
  if (!required || required.length === 0) {
    return true
  }
  return required.some((code) => matchesPermissionCode(code, held))
}
