import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/**
 * meta.titleKey 走 i18n 键；菜单与面包屑均由路由派生（单一数据源）。
 * meta.group 用于侧边栏分组，meta.hidden 不进菜单。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { layout: 'blank', public: true },
  },
  {
    // 独立于 DefaultLayout：预览全屏无侧边栏，容器 100vw/100vh 供 ScreenEngine 铺满
    path: '/screen/preview/:id',
    name: 'ScreenPreview',
    component: () => import('@/views/screen/ScreenPreview.vue'),
    meta: { titleKey: 'screen.preview', hidden: true, layout: 'screen' },
  },
  {
    path: '/s/:code',
    name: 'ScreenShare',
    component: () => import('@/views/screen/ScreenShare.vue'),
    meta: { titleKey: 'screen.share', hidden: true, layout: 'screen', public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'DashboardList',
        component: () => import('@/views/dashboard/DashboardList.vue'),
        meta: { titleKey: 'nav.dashboard', icon: 'Odometer', group: 'nav.groupDesign' },
      },
      {
        path: 'dashboard/editor/:id?',
        name: 'DashboardEditor',
        component: () => import('@/views/dashboard/DashboardEditor.vue'),
        meta: { titleKey: 'dashboard.editor', hidden: true },
      },
      {
        path: 'screen',
        name: 'ScreenList',
        component: () => import('@/views/screen/ScreenList.vue'),
        meta: { titleKey: 'nav.screen', icon: 'Monitor', group: 'nav.groupDesign' },
      },
      {
        path: 'screen/editor/:id?',
        name: 'ScreenEditor',
        component: () => import('@/views/screen/ScreenEditor.vue'),
        meta: { titleKey: 'screen.edit', hidden: true },
      },
      {
        path: 'analysis',
        name: 'Analysis',
        component: () => import('@/views/analysis/AnalysisView.vue'),
        meta: { titleKey: 'nav.analysis', icon: 'DataAnalysis', group: 'nav.groupDesign' },
      },
      {
        path: 'datasource',
        name: 'Datasource',
        component: () => import('@/views/datasource/DatasourceList.vue'),
        meta: { titleKey: 'nav.datasource', icon: 'Connection', group: 'nav.groupData' },
      },
      {
        path: 'etl',
        name: 'EtlDesigner',
        component: () => import('@/views/etl/EtlDesigner.vue'),
        meta: { titleKey: 'nav.etl', icon: 'SetUp', group: 'nav.groupData' },
      },
      {
        path: 'model',
        name: 'Dataset',
        component: () => import('@/views/model/DatasetList.vue'),
        meta: { titleKey: 'nav.model', icon: 'Files', group: 'nav.groupData' },
      },
      {
        path: 'alert',
        name: 'AlertRules',
        component: () => import('@/views/alert/AlertRuleList.vue'),
        meta: { titleKey: 'nav.alert', icon: 'Bell', group: 'nav.groupOps' },
      },
      {
        path: 'monitor',
        name: 'AuditLog',
        component: () => import('@/views/monitor/AuditLog.vue'),
        meta: { titleKey: 'nav.monitor', icon: 'Notebook', group: 'nav.groupOps' },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/SimplePage.vue'),
    props: { code: 403 },
    meta: { public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/SimplePage.vue'),
    props: { code: 404 },
    meta: { public: true },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
})

/** 侧边栏派生用：DefaultLayout 下的可见子路由 */
export const menuRoutes = routes.find((r) => r.path === '/')?.children ?? []
