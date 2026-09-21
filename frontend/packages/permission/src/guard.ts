import type { Router, RouteLocationNormalized } from 'vue-router'

export interface PermissionGuardOptions {
  /** 登录页路径，默认 /login */
  loginPath?: string
  /** 首页路径，登录后默认跳转，默认 / */
  homePath?: string
}

/**
 * 路由权限守卫
 * - 未登录访问任意页面 → 跳登录页（带 redirect）
 * - 已登录访问登录页 → 跳首页（或 redirect 目标）
 */
export function createPermissionGuard(router: Router, options: PermissionGuardOptions = {}): void {
  const loginPath = options.loginPath || '/login'
  const homePath = options.homePath || '/'

  router.beforeEach((to: RouteLocationNormalized, _from, next) => {
    const token = getToken()
    if (to.path === loginPath) {
      if (token) {
        const redirect = to.query.redirect as string | undefined
        next(redirect || homePath)
      } else {
        next()
      }
      return
    }
    const isPublic = to.meta.public as boolean | undefined
    if (isPublic) {
      next()
      return
    }
    if (!token) {
      next({ path: loginPath, query: { redirect: to.fullPath } })
      return
    }
    const permissions = getPermissions()
    const required = (to.meta.permission ?? to.meta.permissions) as string | string[] | undefined
    if (required) {
      const list = Array.isArray(required) ? required : [required]
      const hasPermission = permissions.includes('*') || list.some((p) => permissions.includes(p))
      if (!hasPermission) {
        next({ path: '/403' })
        return
      }
    }
    next()
  })
}

function getToken(): string | null {
  const value = readStored('dataviz_access_token')
  return typeof value === 'string' && value ? value : null
}

function getPermissions(): string[] {
  const value = readStored('dataviz_permissions')
  return Array.isArray(value) ? (value as string[]) : []
}

/** 兼容 shared-utils setLocal 的 {value, expireAt} 信封格式与裸值格式 */
function readStored(key: string): unknown {
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
