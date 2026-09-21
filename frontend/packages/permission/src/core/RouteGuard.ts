import type { Router, RouteLocationNormalized, NavigationGuardNext } from 'vue-router'
import { PermissionManager } from './PermissionManager'

/**
 * 路由元信息中的权限配置
 */
export interface RoutePermissionMeta {
  /** 所需权限码 */
  permissions?: string[]
  /** 所需角色 */
  roles?: string[]
  /** 是否需要登录 */
  requiresAuth?: boolean
  /** 是否公开访问 */
  public?: boolean
}

/**
 * 创建并安装权限路由守卫请使用 `@dataviz/permission/guard` 中的 createPermissionGuard，
 * 本类保留用于自定义 token 来源的场景。
 */
export class RouteGuard {
  private permissionManager: PermissionManager
  private loginRoute = '/login'
  private homeRoute = '/'
  private getToken: () => string | null

  constructor(
    permissionManager: PermissionManager,
    getToken: () => string | null,
    options?: { loginRoute?: string; homeRoute?: string }
  ) {
    this.permissionManager = permissionManager
    this.getToken = getToken
    if (options?.loginRoute) this.loginRoute = options.loginRoute
    if (options?.homeRoute) this.homeRoute = options.homeRoute
  }

  /**
   * 安装路由守卫到 Router
   */
  install(router: Router): void {
    router.beforeEach(this.beforeEach.bind(this))
  }

  /**
   * beforeEach 导航守卫
   */
  private beforeEach(
    to: RouteLocationNormalized,
    _from: RouteLocationNormalized,
    next: NavigationGuardNext
  ): void {
    const meta = to.meta as RoutePermissionMeta
    const token = this.getToken()

    // 公开页面，直接放行
    if (meta.public) {
      next()
      return
    }

    // 未登录
    if (!token) {
      if (meta.requiresAuth !== false) {
        next({ path: this.loginRoute, query: { redirect: to.fullPath } })
        return
      }
      next()
      return
    }

    // 已登录但访问登录页 -> 跳转首页
    if (to.path === this.loginRoute) {
      next({ path: this.homeRoute })
      return
    }

    // 检查角色权限
    if (meta.roles && meta.roles.length > 0) {
      if (!this.permissionManager.hasAnyRole(meta.roles)) {
        next({ path: '/403' })
        return
      }
    }

    // 检查操作权限
    if (meta.permissions && meta.permissions.length > 0) {
      if (!this.permissionManager.hasAnyPermission(meta.permissions)) {
        next({ path: '/403' })
        return
      }
    }

    next()
  }
}
