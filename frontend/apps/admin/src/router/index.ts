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
        // 调度任务归平台管理：它决定的是"服务端什么时候替谁跑一段代码"，不是某个租户的内容。
        // 用后端真实码 schedule:read / schedule:write —— 这批码在 2026-10-16-r1-permission-codes.sql 里，
        // 补丁未执行前只有 super_admin 能进（超管走 '*' 通配），其他角色要等码表落库。
        path: 'schedule',
        name: 'ScheduleJobs',
        component: () => import('@/views/ScheduleJobs.vue'),
        meta: { titleKey: 'nav.schedule', icon: 'Timer', group: 'nav.groupPlatform', permission: 'schedule:read' },
      },
      {
        // 监控面是全局的：service_instance / monitor_metric 这两张表没有租户列（在拦截器黑名单里），
        // 所以这一页任何有 monitor:read 的人都看到同一份事实。
        path: 'monitor',
        name: 'ServiceMonitor',
        component: () => import('@/views/ServiceMonitor.vue'),
        meta: { titleKey: 'nav.monitor', icon: 'TrendCharts', group: 'nav.groupPlatform', permission: 'monitor:read' },
      },
      {
        path: 'open-platform',
        name: 'OpenPlatform',
        component: () => import('@/views/OpenPlatform.vue'),
        meta: { titleKey: 'nav.openPlatform', icon: 'Connection', group: 'nav.groupPlatform', permission: 'openapi:read' },
      },
      {
        // 审批中心：流水与裁决都在 collab-service，通过后不会自动上线、也不会通知申请人，
        // 页面文案把这一点写死了（见 approval.noAutoPublish）。
        path: 'approvals',
        name: 'ApprovalCenter',
        component: () => import('@/views/ApprovalCenter.vue'),
        meta: { titleKey: 'nav.approvals', icon: 'Stamp', group: 'nav.groupPlatform', permission: 'collab:read' },
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
      {
        // 通知渠道归管理端而不是设计端：这一行存的是服务端将来要拿去 POST 的地址与机器人 token，
        // 属于运维配置面。权限用后端真实码 alert:read / alert:write（路线乙），不新增权限行。
        path: 'channels',
        name: 'NotifyChannelConfig',
        component: () => import('@/views/ChannelConfig.vue'),
        meta: { titleKey: 'nav.channels', icon: 'Bell', group: 'nav.groupSystem', permission: 'alert:read' },
      },
      {
        // 收件人从渠道配置搬到这里的理由见进度表 D73：一条渠道在服务端只会被取一条启用记录，
        // 所以收件人写在渠道里等于"所有规则共用一批人"，改不动也按不了规则分。
        // 权限沿用 alert:read / alert:write，不新增码（这批地址原先就在渠道 config 里，可读面没变大）。
        path: 'contacts',
        name: 'AlertContactConfig',
        component: () => import('@/views/ContactConfig.vue'),
        meta: { titleKey: 'nav.contacts', icon: 'UserFilled', group: 'nav.groupSystem', permission: 'alert:read' },
      },
      {
        path: 'notify-groups',
        name: 'AlertNotifyGroupConfig',
        component: () => import('@/views/NotifyGroupConfig.vue'),
        meta: { titleKey: 'nav.notifyGroups', icon: 'Collection', group: 'nav.groupSystem', permission: 'alert:read' },
      },
      {
        // 行列权限规则放在管理端而不是设计端：它决定的是"谁能看到哪些行、哪些列"，属于运维口径。
        // 权限沿用 analysis:read / analysis:write（配规则是"自助分析"能力的一部分），不新增权限行。
        path: 'perm-rules',
        name: 'DataPermRuleConfig',
        component: () => import('@/views/PermRuleConfig.vue'),
        meta: { titleKey: 'nav.permRules', icon: 'Lock', group: 'nav.groupSystem', permission: 'analysis:read' },
      },
      {
        // file:read / file:write 是 D66 码表里的两档，补丁已执行（AW5）。此前它们在后端有注解、前端零入口，
        // 这一页就是那个缺口。/view/{id} 仍是免登直读，所以上传后的大屏背景图不需要登录也能显示。
        path: 'files',
        name: 'FileManage',
        component: () => import('@/views/FileManage.vue'),
        meta: { titleKey: 'nav.files', icon: 'FolderOpened', group: 'nav.groupSystem', permission: 'file:read' },
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
