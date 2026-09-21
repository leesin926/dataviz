import { router } from '@/router'
import { useUserStore } from '@/stores/user'

/**
 * 路由权限守卫 (简化版,实际由 @dataviz/permission 模块提供)
 */
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  // 公开路由直接放行
  if (to.meta.public) {
    next()
    return
  }
  // 需要登录
  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 权限判断
  const required = to.meta.permission as string | string[] | undefined
  if (required) {
    const list = Array.isArray(required) ? required : [required]
    const has = list.some((p) => userStore.hasPermission(p))
    if (!has) {
      next({ path: '/403' })
      return
    }
  }
  next()
})
