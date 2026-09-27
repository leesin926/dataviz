import type { Router, RouteLocationNormalized, RouteMeta } from 'vue-router'
import { matchesAnyPermissionCode } from './core/matchesPermissionCode'
import { readSessionPermissions, readSessionToken } from './core/session'

export interface PermissionGuardOptions {
  /** 登录页路径，默认 /login */
  loginPath?: string
  /** 首页路径，登录后默认跳转，默认 / */
  homePath?: string
}

/**
 * 当前会话能否进入某个路由 —— 判据只有一条：`meta.permission`（或 `meta.permissions`）里要求的码
 * 是否被持有的权限命中；没有声明要求的路由视为可进。
 * <p>
 * 与后端 `PermissionInterceptor.matches` 同一套匹配语义（精确 / `*` / `xxx:*` 前缀通）。
 * 这里以前只做 `includes`，于是被授予 `system:*` 的角色会"接口通、菜单不见"。
 * </p>
 * 导出它是因为"能不能进"这个问题不止守卫在问：403 页要找"第一个真能回去的页面"也问同一个问题，
 * 两处各写一份的话，分叉的症状正好是"按钮说能回、点进去又是 403"。
 */
export function canEnterRouteMeta(meta: RouteMeta | undefined, permissions: string[]): boolean {
  const required = (meta?.permission ?? meta?.permissions) as string | string[] | undefined
  if (!required) {
    return true
  }
  const list = Array.isArray(required) ? required : [required]
  return matchesAnyPermissionCode(list, permissions)
}

/**
 * 路由权限守卫
 * - 未登录访问任意页面 → 跳登录页（带 redirect）
 * - 已登录访问登录页 → 跳首页（或 redirect 目标）；**目标本身也进不去时留在登录页**。
 *   否则无权限的账号会被弹进 403 且再也回不到登录页——既出不去也换不了号。
 */
export function createPermissionGuard(router: Router, options: PermissionGuardOptions = {}): void {
  const loginPath = options.loginPath || '/login'
  const homePath = options.homePath || '/'

  router.beforeEach((to: RouteLocationNormalized, _from, next) => {
    const token = readSessionToken()
    if (to.path === loginPath) {
      if (!token) {
        next()
        return
      }
      const target = (to.query.redirect as string | undefined) || homePath
      if (canEnterRouteMeta(router.resolve(target).meta, readSessionPermissions())) {
        next(target)
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
    if (!canEnterRouteMeta(to.meta, readSessionPermissions())) {
      next({ path: '/403' })
      return
    }
    next()
  })
}
