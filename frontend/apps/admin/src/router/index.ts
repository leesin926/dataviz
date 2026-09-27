import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { createPermissionGuard } from '@dataviz/permission'

/**
 * meta.titleKey 驱动菜单与面包屑（单一数据源，支持 i18n）。
 * history base 为 /admin，故子路由路径不再重复 admin 前缀。
 *
 * meta.permission 用的是**后端 sys_permission 里真实存在的码**（路线乙，见进度表 D56）：
 * 这里写的码与 controller 上 @RequiresPermission 的码同源，前端拦一次是"菜单不该看见"，
 * 后端拦一次才是"真不能调"。之前这 8 个页面用的是 `admin:*:view` —— 后端根本没有这些码，
 * 结果是非超管永远进不来、超管永远看不出差别（缺陷 API-26）。
 * 粒度只有"模块 + 读写两档"，所以平台管理面 4 个页面共用 `platform:read`，
 * 想再细分（"能看审计不能看配置"）得先把码表粒度改掉，不是在 meta 里改字符串。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { titleKey: 'nav.dashboard', icon: 'Odometer', group: 'nav.groupOverview', permission: 'platform:read' },
      },
      {
        path: 'tenant',
        name: 'TenantManage',
        component: () => import('@/views/TenantManage.vue'),
        meta: { titleKey: 'nav.tenant', icon: 'OfficeBuilding', group: 'nav.groupPlatform', permission: 'platform:read' },
      },
      {
        path: 'license',
        name: 'LicenseManage',
        component: () => import('@/views/LicenseManage.vue'),
        meta: { titleKey: 'nav.license', icon: 'Key', group: 'nav.groupPlatform', permission: 'platform:read' },
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/SystemConfig.vue'),
        meta: { titleKey: 'nav.config', icon: 'Setting', group: 'nav.groupPlatform', permission: 'platform:read' },
      },
      {
        path: 'users',
        name: 'AdminUserManage',
        component: () => import('@/views/UserManage.vue'),
        meta: { titleKey: 'nav.users', icon: 'User', group: 'nav.groupSystem', permission: 'system:user:list' },
      },
      {
        path: 'depts',
        name: 'AdminDeptManage',
        component: () => import('@/views/DeptManage.vue'),
        meta: { titleKey: 'nav.depts', icon: 'Grid', group: 'nav.groupSystem', permission: 'system:dept:list' },
      },
      {
        path: 'roles',
        name: 'AdminRoleManage',
        component: () => import('@/views/RoleManage.vue'),
        meta: { titleKey: 'nav.roles', icon: 'Avatar', group: 'nav.groupSystem', permission: 'system:role:list' },
      },
      {
        path: 'menus',
        name: 'AdminMenuManage',
        component: () => import('@/views/MenuManage.vue'),
        meta: { titleKey: 'nav.menus', icon: 'Menu', group: 'nav.groupSystem', permission: 'system:menu:list' },
      },
      {
        path: 'audit',
        name: 'AdminAuditLog',
        component: () => import('@/views/AuditLog.vue'),
        meta: { titleKey: 'nav.audit', icon: 'Notebook', group: 'nav.groupSystem', permission: 'platform:read' },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/SimplePage.vue'),
    props: { code: 403 },
    meta: { public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/SimplePage.vue'),
    props: { code: 404 },
    meta: { public: true },
  },
]

const router = createRouter({
  history: createWebHistory('/admin'),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
})

createPermissionGuard(router, { loginPath: '/login', homePath: '/dashboard' })

/** 侧边栏派生用：AdminLayout 下的可见子路由 */
export const menuRoutes = routes.find((r) => r.path === '/')?.children ?? []

export default router
