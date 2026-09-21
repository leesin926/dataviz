import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { createPermissionGuard } from '@dataviz/permission'

/**
 * meta.titleKey 驱动菜单与面包屑（单一数据源，支持 i18n）。
 * history base 为 /admin，故子路由路径不再重复 admin 前缀。
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
        meta: { titleKey: 'nav.dashboard', icon: 'Odometer', group: 'nav.groupOverview', permission: 'admin:dashboard:view' },
      },
      {
        path: 'tenant',
        name: 'TenantManage',
        component: () => import('@/views/TenantManage.vue'),
        meta: { titleKey: 'nav.tenant', icon: 'OfficeBuilding', group: 'nav.groupPlatform', permission: 'admin:tenant:view' },
      },
      {
        path: 'license',
        name: 'LicenseManage',
        component: () => import('@/views/LicenseManage.vue'),
        meta: { titleKey: 'nav.license', icon: 'Key', group: 'nav.groupPlatform', permission: 'admin:license:view' },
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/SystemConfig.vue'),
        meta: { titleKey: 'nav.config', icon: 'Setting', group: 'nav.groupPlatform', permission: 'admin:config:view' },
      },
      {
        path: 'users',
        name: 'AdminUserManage',
        component: () => import('@/views/UserManage.vue'),
        meta: { titleKey: 'nav.users', icon: 'User', group: 'nav.groupSystem', permission: 'admin:user:view' },
      },
      {
        path: 'roles',
        name: 'AdminRoleManage',
        component: () => import('@/views/RoleManage.vue'),
        meta: { titleKey: 'nav.roles', icon: 'Avatar', group: 'nav.groupSystem', permission: 'admin:role:view' },
      },
      {
        path: 'menus',
        name: 'AdminMenuManage',
        component: () => import('@/views/MenuManage.vue'),
        meta: { titleKey: 'nav.menus', icon: 'Menu', group: 'nav.groupSystem', permission: 'admin:menu:view' },
      },
      {
        path: 'audit',
        name: 'AdminAuditLog',
        component: () => import('@/views/AuditLog.vue'),
        meta: { titleKey: 'nav.audit', icon: 'Notebook', group: 'nav.groupSystem', permission: 'admin:audit:view' },
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
